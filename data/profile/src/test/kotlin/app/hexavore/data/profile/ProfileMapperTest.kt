package app.hexavore.data.profile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.hexavore.core.database.HexavoreDatabase
import app.hexavore.core.database.entity.GoalEntity
import app.hexavore.core.database.entity.ProfileEntity
import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.core.testing.TestDispatchers
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.profile.Activity
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.UnitSystem
import app.hexavore.domain.profile.WeeklySessions
import app.hexavore.domain.profile.WorkActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Ce qu'une base écrite par une version plus récente ne doit pas casser.
 *
 * Cette propriété **n'appartient pas au contrat des ports** et ne peut pas y figurer :
 * les énumérations du domaine sont fermées, donc aucun appelant de `Profiles` ou de
 * `Goals` ne peut soumettre une valeur que le mapper aurait à replier. Le seul chemin
 * qui l'atteint est celui d'une base déjà écrite — une sauvegarde restaurée depuis une
 * version plus récente, ou une rétrogradation d'application.
 *
 * Les tests écrivent donc **directement l'entité**, sans passer par le port. Le repli
 * était documenté depuis la tranche 4 ; rien ne l'éprouvait, et un repli qui plante est
 * un profil inaccessible, donc une application inutilisable.
 *
 * **Chaque repli est choisi pour être le moins engageant** : sous-estimer la dépense
 * plutôt que la surestimer, et appliquer la moyenne des deux formules pour un sexe non
 * reconnu — exactement ce que « je préfère ne pas répondre » demande déjà.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class ProfileMapperTest {
    private lateinit var base: HexavoreDatabase
    private lateinit var magasin: RoomProfileStore

    @Before
    fun ouvrir() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        base = Room.inMemoryDatabaseBuilder(context, HexavoreDatabase::class.java).build()
        magasin = RoomProfileStore(
            profiles = base.profileDao(),
            goalDao = base.goalDao(),
            ids = SequentialIdGenerator("pesee"),
            clock = FixedClock(MAINTENANT),
            dispatchers = TestDispatchers(Dispatchers.IO),
        )
    }

    @After
    fun fermer() = base.close()

    @Test
    fun `un sexe inconnu applique la moyenne des deux formules`() = runBlocking {
        ecrireProfil(sex = "MARTIEN")

        assertEquals(Sex.UNSPECIFIED, magasin.observeProfile().first()!!.sex)
    }

    @Test
    fun `un metier inconnu retombe sur le plus bas`() = runBlocking {
        // Le moins engageant : sous-estimer la depense plutot que la surestimer.
        ecrireProfil(work = "ATHLETE_OLYMPIQUE", sessions = 0)

        assertEquals(Activity(WorkActivity.DESK), magasin.observeProfile().first()!!.activity)
    }

    @Test
    fun `un ancien niveau unique se traduit en couple`() = runBlocking {
        // Une base migree n'en porte plus, mais un fichier bricole ou une version
        // plus ancienne le peut : la lecture prudente vaut mieux qu'un profil refuse.
        ecrireProfil(work = "MODERATE", sessions = 0)

        assertEquals(
            Activity(WorkActivity.ON_FEET, WeeklySessions(3)),
            magasin.observeProfile().first()!!.activity,
        )
    }

    @Test
    fun `des seances hors plage reviennent dans la plage`() = runBlocking {
        ecrireProfil(sessions = 99)

        assertEquals(WeeklySessions.MAX_SESSIONS, magasin.observeProfile().first()!!.activity.sessions.count)
    }

    @Test
    fun `un systeme d unites inconnu retombe sur le metrique`() = runBlocking {
        // Le stockage est toujours metrique : c'est l'affichage qui varie.
        ecrireProfil(unitSystem = "COUDEES")

        assertEquals(UnitSystem.METRIC, magasin.observeProfile().first()!!.unitSystem)
    }

    @Test
    fun `un profil dont toutes les enumerations sont inconnues reste lisible`() = runBlocking {
        // Le cas qui compte vraiment : trois replis a la fois, et le profil revient
        // quand meme. Planter ici rendrait l'application entiere inaccessible.
        ecrireProfil(sex = "MARTIEN", work = "ATHLETE_OLYMPIQUE", unitSystem = "COUDEES")

        val profil = magasin.observeProfile().first()!!
        assertEquals(TAILLE_CM, profil.heightCm, 0.0)
        assertEquals(Sex.UNSPECIFIED, profil.sex)
    }

    @Test
    fun `une provenance et une strategie inconnues retombent sur le calcul et le maintien`() = runBlocking {
        ecrireObjectif(origin = "IMPORTE_DE_MARS", strategy = "SECHE_EXTREME")

        val objectif = magasin.observeCurrent().first()!!
        assertEquals(GoalOrigin.CALCULATED, objectif.origin)
        assertEquals(GoalStrategy.MAINTAIN, objectif.strategy)
    }

    private suspend fun ecrireProfil(
        sex: String = Sex.MALE.name,
        work: String = WorkActivity.ON_FEET.name,
        sessions: Int = 3,
        unitSystem: String = UnitSystem.METRIC.name,
    ) = base.profileDao().upsert(
        ProfileEntity(
            birthDate = "1991-03-04",
            sex = sex,
            heightCm = TAILLE_CM,
            activityLevel = work,
            leisureSessions = sessions,
            unitSystem = unitSystem,
            createdAt = INSTANT_MILLIS,
            updatedAt = INSTANT_MILLIS,
        ),
    )

    private suspend fun ecrireObjectif(
        origin: String = GoalOrigin.CALCULATED.name,
        strategy: String = GoalStrategy.MAINTAIN.name,
    ) = base.goalDao().insert(
        GoalEntity(
            id = "goal-relu",
            startedAt = "2026-06-01",
            endedAt = null,
            activeKey = GoalEntity.ACTIVE,
            origin = origin,
            strategy = strategy,
            targetWeightKg = null,
            targetDate = null,
            kcal = 2_000.0,
            proteinG = 112.0,
            carbG = 223.0,
            sugarG = 50.0,
            fatG = 67.0,
            fiberG = 28.0,
            createdAt = INSTANT_MILLIS,
        ),
    )

    private companion object {
        val MAINTENANT: Instant = Instant.parse("2026-08-10T10:00:00Z")
        val INSTANT_MILLIS: Long = MAINTENANT.toEpochMilli()
        const val TAILLE_CM = 182.0
    }
}
