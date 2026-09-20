package app.hexavore.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.hexavore.core.database.HexavoreDatabase
import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryAdjustmentSettings
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.core.testing.TestDispatchers
import app.hexavore.data.diary.RoomDiaryRepository
import app.hexavore.data.diary.RoomFavoriteDishes
import app.hexavore.data.food.toEntity
import app.hexavore.data.profile.RoomProfileStore
import app.hexavore.domain.backup.SnapshotRead
import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.EntryId
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.FavoriteComponent
import app.hexavore.domain.diary.FavoriteDish
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.diary.FoodEntry
import app.hexavore.domain.diary.MealMoment
import app.hexavore.domain.diary.QuantityUnit
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.goal.AdjustmentSetup
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.Goal
import app.hexavore.domain.goal.GoalId
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.nutrition.Macros
import app.hexavore.domain.nutrition.NutrientValues
import app.hexavore.domain.profile.ActivityLevel
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.UserProfile
import app.hexavore.domain.profile.WeightEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.util.zip.GZIPInputStream

/**
 * **Export, effacement complet, import : l'état est identique.**
 *
 * C'est le critère de fin de la tranche 8, écrit tel quel dans [docs/12][plan], et ce
 * cas le joue littéralement — sur la vraie base, en écrivant par les **vrais** dépôts
 * et en relisant par eux.
 *
 * Ce détour n'est pas décoratif. `RoomSnapshotStore` emprunte les mappeurs des trois
 * modules qui les portent déjà, plutôt que d'en écrire un second jeu ; si l'emprunt
 * cessait un jour d'en être un, la sauvegarde écrirait des lignes que l'application
 * relirait de travers. Passer par les dépôts est ce qui rend cette divergence visible.
 *
 * [plan]: docs/12-plan-de-developpement.md
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class BackupRoundTripTest {
    private lateinit var base: HexavoreDatabase
    private lateinit var store: RoomSnapshotStore
    private lateinit var journal: RoomDiaryRepository
    private lateinit var profils: RoomProfileStore
    private lateinit var favoris: RoomFavoriteDishes

    private val codec = JsonSnapshotCodec(dispatchers)
    private val adaptation = InMemoryAdjustmentSettings(AdjustmentSetup(enabled = false, lastIgnoredOn = LUNDI))

    @Before
    fun ouvrir() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        base = Room.inMemoryDatabaseBuilder(context, HexavoreDatabase::class.java).build()

        journal = RoomDiaryRepository(base.diaryDao(), base.calendarDao(), base.favoriteDishDao(), horloge)
        profils = RoomProfileStore(base.profileDao(), base.goalDao(), SequentialIdGenerator("p"), horloge, dispatchers)
        favoris = RoomFavoriteDishes(base.favoriteDishDao(), horloge, dispatchers)
        store = RoomSnapshotStore(
            database = base,
            reads = base.backupReadDao(),
            writes = base.backupWriteDao(),
            adjustment = adaptation,
            clock = horloge,
            dispatchers = dispatchers,
        )
    }

    @After
    fun fermer() = base.close()

    @Test
    fun `ce qui a ete ecrit ressort a l identique apres effacement et restauration`() = runBlocking {
        remplir()
        val fichier = codec.encode(store.capture())

        store.erase()
        assertTrue("l'effacement laisse une base vide", journal.observeDay(LUNDI).first().isEmpty())

        store.replace((codec.decode(fichier) as SnapshotRead.Readable).snapshot)

        assertEquals(PROFIL, profils.observeProfile().first())
        assertEquals(listOf(OBJECTIF), profils.observeAll().first())
        assertEquals(listOf(PESEE), profils.observeHistory().first())
        assertEquals(listOf(PLAT), journal.observeDay(LUNDI).first())
        assertEquals(listOf(FAVORI), favoris.observeAll().first())
    }

    @Test
    fun `la fiche citee par une ligne survit au passage`() = runBlocking {
        // Sans elle, une restauration hors-ligne afficherait un journal d'entrees
        // anonymes -- et le lien que chaque ligne tient vers sa fiche serait rompu.
        remplir()
        val fichier = codec.encode(store.capture())
        store.erase()
        store.replace((codec.decode(fichier) as SnapshotRead.Readable).snapshot)

        assertEquals(ALIMENT.id, journal.observeDay(LUNDI).first().single().entries.single().foodId)
        assertEquals(ALIMENT, store.capture().foods.single())
    }

    @Test
    fun `l etat de l adaptation voyage avec le reste`() = runBlocking {
        remplir()

        val instantane = (codec.decode(codec.encode(store.capture())) as SnapshotRead.Readable).snapshot

        assertFalse(
            "« ne plus proposer » ne doit pas s'oublier au changement d'appareil",
            instantane.adjustment.enabled,
        )
        assertEquals(LUNDI, instantane.adjustment.lastIgnoredOn)
    }

    @Test
    fun `restaurer repose l etat de l adaptation`() = runBlocking {
        // **Le cas precedent s'arretait un pas trop tot.** Il verifiait que l'etat
        // voyage dans le fichier, jamais qu'on le repose en arrivant -- et `replace`
        // le laissait tomber. Quelqu'un qui restaurait retrouvait ses repas sans son
        // « ne plus proposer », et la carte revenait le lendemain sans explication.
        remplir()
        val fichier = codec.encode(store.capture())
        // L'appareil d'arrivee n'a jamais rien repondu : c'est ce que la restauration
        // doit ecraser.
        adaptation.restore(AdjustmentSetup())

        store.replace((codec.decode(fichier) as SnapshotRead.Readable).snapshot)

        assertFalse("« ne plus proposer » doit survivre au changement d'appareil", adaptation.setup.enabled)
        assertEquals(LUNDI, adaptation.setup.lastIgnoredOn)
    }

    @Test
    fun `effacer ne laisse rien`() = runBlocking {
        remplir()

        store.erase()

        val vide = store.capture()

        assertNull(vide.profile)
        assertEquals(emptyList<Goal>(), vide.goals)
        assertEquals(emptyList<WeightEntry>(), vide.weights)
        assertEquals(emptyList<Dish>(), vide.dishes)
        assertEquals(emptyList<Food>(), vide.foods)
        assertEquals(emptyList<FavoriteDish>(), vide.favorites)
    }

    // --- Ce que le fichier ne porte pas -------------------------------------------

    @Test
    fun `aucune cle d API ne figure dans le fichier`() = runBlocking {
        // La cle est rangee dans le fichier de preferences de l'IA, que ce module ne
        // lit pas. Le cas l'ecrit **la ou elle vit vraiment**, puis cherche la chaine
        // dans les octets produits : c'est la seule facon d'eprouver une absence.
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("anthropic.key", CLE_INVENTEE)
            .commit()
        remplir()

        val texte = decompresse(codec.encode(store.capture()))

        assertFalse("une cle d'API ne sort jamais de l'appareil", texte.contains(CLE_INVENTEE))
    }

    @Test
    fun `le fichier ne porte que les sections attendues`() = runBlocking {
        // Le vrai risque n'est pas qu'une cle s'y trouve aujourd'hui, c'est qu'une
        // section s'y ajoute demain sans que personne y pense. Ce cas tombe des qu'une
        // clef de premier niveau apparait ou disparait.
        remplir()

        val sections = SECTION.findAll(decompresse(codec.encode(store.capture()))).map { it.groupValues[1] }.toSet()

        assertEquals(SECTIONS_ATTENDUES, sections)
    }

    @Test
    fun `un fichier plus recent est refuse, jamais importe a moitie`() = runBlocking {
        remplir()
        val futur = decompresse(codec.encode(store.capture()))
            .replace("\"formatVersion\": 1", "\"formatVersion\": 99")

        val lu = codec.decode(compresse(futur))

        assertEquals(SnapshotRead.TooRecent(99), lu)
    }

    @Test
    fun `un fichier illisible est un resultat, pas une exception`() = runBlocking {
        assertEquals(SnapshotRead.Unreadable, codec.decode("ceci n'est pas du gzip".toByteArray()))
        assertEquals(SnapshotRead.Unreadable, codec.decode(compresse("{ \"quoi\": true }")))
    }

    // --- Le decor -------------------------------------------------------------------

    private suspend fun remplir() {
        base.foodDao().upsert(ALIMENT.toEntity(MAINTENANT.toEpochMilli()))
        profils.save(PROFIL)
        profils.replace(OBJECTIF)
        profils.record(PESEE)
        journal.save(PLAT)
        favoris.save(FAVORI)
    }

    private companion object {
        val LUNDI: LocalDate = LocalDate.of(2026, 8, 17)
        val MAINTENANT: Instant = Instant.parse("2026-08-17T10:00:00Z")
        val horloge = FixedClock(MAINTENANT)
        val dispatchers = TestDispatchers(Dispatchers.IO)

        const val CLE_INVENTEE = "sk-ant-ceci-est-un-secret-qui-ne-doit-jamais-sortir"

        val SECTION = Regex("^ {4}\"([a-zA-Z]+)\":", RegexOption.MULTILINE)
        val SECTIONS_ATTENDUES = setOf(
            "formatVersion", "appVersion", "exportedAt", "attribution",
            "profile", "goals", "weights", "dishes", "entries", "foods", "favorites", "adjustment",
        )

        val PROFIL = UserProfile(
            birthDate = LocalDate.of(1991, 3, 4),
            sex = Sex.MALE,
            heightCm = 182.0,
            activityLevel = ActivityLevel.MODERATE,
        )

        val OBJECTIF = Goal(
            id = GoalId("objectif"),
            startedAt = LUNDI.minusDays(30),
            origin = GoalOrigin.CALCULATED,
            strategy = GoalStrategy.LOSE,
            targetWeightKg = 80.0,
            targetDate = LUNDI.plusDays(100),
            daily = DailyGoal(2400.0, 150.0, 255.0, 60.0, 67.0, 30.0),
        )

        val PESEE = WeightEntry(date = LUNDI, weightKg = 88.4)

        val ALIMENT = Food(
            id = FoodId("aliment"),
            source = FoodSource.OFF,
            sourceRef = "3017620422003",
            name = "Pate a tartiner",
            brand = "Une marque",
            // Une valeur inconnue et une valeur completee : les deux distinctions que
            // le projet defend le plus, et qui disparaitraient au premier `?: 0.0`.
            per100g = NutrientValues(kcal = 539.0, protein = 6.3, carbs = 57.5, sugars = null, fat = 30.9),
            // Ni portions nommees ni teneurs completees : la table locale ne les
            // stocke pas, ce sont des proprietes de la reference (D54). Les mettre
            // dans le decor ferait echouer ce cas pour une raison qui n'est pas la
            // sienne -- et masquerait celles qui le concernent.
            defaultServingG = 15.0,
            isLiquid = false,
            fetchedAt = MAINTENANT,
            useCount = 3,
        )

        val PLAT = Dish(
            id = DishId("plat"),
            date = LUNDI,
            loggedAt = MAINTENANT,
            source = EntrySource.BARCODE,
            entries = listOf(
                FoodEntry(
                    id = EntryId("ligne"),
                    dishId = DishId("plat"),
                    foodId = FoodId("aliment"),
                    displayName = "Pate a tartiner",
                    quantity = 2.0,
                    unit = "1 portion",
                    grams = 30.0,
                    macros = Macros(kcal = 161.7, protein = 1.9, carbs = 17.3, sugars = null, fat = 9.3, fiber = null),
                ),
            ),
            // Le nom et le moment voyagent avec le reste : la sauvegarde porte ce que
            // la base tient (D96), et le cas d'aller-retour compare le plat entier.
            title = "Poke bowl",
            moment = MealMoment.DINNER,
        )

        val FAVORI = FavoriteDish(
            id = FavoriteDishId("favori"),
            name = "Petit dejeuner",
            useCount = 7,
            components = listOf(
                FavoriteComponent(
                    foodId = FoodId("aliment"),
                    name = "Pate a tartiner",
                    quantity = 2.0,
                    unit = QuantityUnit.Serving("1 portion", 15.0),
                    grams = 30.0,
                    values = NutrientValues(kcal = 161.7, protein = 1.9),
                ),
            ),
        )

        fun decompresse(bytes: ByteArray): String =
            GZIPInputStream(bytes.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)

        fun compresse(texte: String): ByteArray = java.io.ByteArrayOutputStream()
            .also { out -> java.util.zip.GZIPOutputStream(out).use { it.write(texte.toByteArray()) } }
            .toByteArray()
    }
}

/** Le même palier que partout ailleurs : Robolectric ne suit pas encore le 35. */
internal const val ROBOLECTRIC_SDK = 34
