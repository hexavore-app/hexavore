package app.hexavore.data.diary

import app.hexavore.core.testing.firstAfter
import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.EntryId
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.FoodEntry
import app.hexavore.domain.diary.MealMoment
import app.hexavore.domain.food.FoodCitations
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.nutrition.Macros
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Ce que **les deux** implémentations du journal doivent tenir.
 *
 * `DiaryRepository` était le septième et dernier port à deux implémentations sans
 * contrat, celui que [D53][decisions] laissait explicitement de côté et que
 * [D57][decisions] a reconduit. Il est aussi le plus ancien — la tranche 1 — donc
 * celui dont les deux côtés ont eu le plus de temps pour diverger sans que rien ne
 * le dise.
 *
 * Ce jeu est écrit une fois et exécuté deux fois : sur `InMemoryDiaryRepository` et
 * sur `RoomDiaryRepository` sous Robolectric, côte à côte dans le même rapport.
 *
 * **Ce qu'il ne prouve pas.** Ni l'affichage, ni les cas d'usage qui appellent ce
 * port — c'est `DeleteEntry` et non le port qui décide de supprimer un plat vidé de
 * sa dernière ligne, et cette règle-là est éprouvée dans `:domain`.
 *
 * [decisions]: docs/11-decisions.md
 */
abstract class DiaryContract {
    /**
     * Le journal sous test, vide, **et ce qui s'y adosse**.
     *
     * Trois choses au même stockage, parce que la propriété qui compte pour
     * [FoodCitations] est un lien : ce qu'on écrit d'un côté doit se compter de
     * l'autre. Deux fabriques séparées rendraient ce lien indémontrable.
     */
    protected abstract fun open(): OpenJournal

    /** Le journal seul, pour les cas qui ne regardent pas les citations. */
    protected fun journal(): DiaryRepository = open().diary

    /**
     * Un journal ouvert : le port, son compteur, et de quoi faire exister une fiche.
     */
    protected class OpenJournal(
        val diary: DiaryRepository,
        val citations: FoodCitations,
        /**
         * Fait exister la fiche citée.
         *
         * Room le **doit** : `food_entry.food_id` porte une clé étrangère, et une
         * citation vers rien est refusée. Le faux n'a pas de catalogue, donc rien à
         * faire — asymétrie que ce contrat ne peut pas résorber, et qui est écrite
         * en [D71][decisions] plutôt que cachée.
         *
         * [decisions]: docs/11-decisions.md
         */
        val givenFood: suspend (FoodId) -> Unit,
    )

    // --- Lire une journée -------------------------------------------------------

    @Test
    fun `une journee sans saisie rend une liste vide`() = runBlocking {
        // Et non des plats a zero : le calendrier et l'adaptation hebdomadaire
        // doivent distinguer « rien note » de « rien mange ».
        assertEquals(emptyList<Dish>(), journal().observeDay(LUNDI).first())
    }

    @Test
    fun `une journee ne rend que ses propres plats`() = runBlocking {
        val diary = journal()
        diary.save(petitDejeuner(LUNDI))
        diary.save(petitDejeuner(MARDI, plat = AUTRE_PLAT))

        assertEquals(listOf(PLAT), diary.observeDay(LUNDI).first().map { it.id })
    }

    @Test
    fun `les plats sortent du plus ancien au plus recent`() = runBlocking {
        // L'ordre d'ecriture n'est pas l'ordre d'affichage : on peut noter le diner
        // avant de rattraper le petit-dejeuner.
        val diary = journal()
        diary.save(petitDejeuner(LUNDI, plat = AUTRE_PLAT, loggedAt = SOIR))
        diary.save(petitDejeuner(LUNDI))

        assertEquals(listOf(PLAT, AUTRE_PLAT), diary.observeDay(LUNDI).first().map { it.id })
    }

    @Test
    fun `un plat se relit avec ses lignes`() = runBlocking {
        val diary = journal()
        val plat = petitDejeuner(LUNDI)

        diary.save(plat)

        assertEquals(plat, diary.dish(PLAT))
    }

    @Test
    fun `un plat inconnu ne se relit pas`() = runBlocking {
        assertNull(journal().dish(DishId("jamais-ecrit")))
    }

    // --- Ce que les valeurs deviennent ------------------------------------------

    @Test
    fun `une valeur inconnue le reste, elle ne devient jamais zero`() = runBlocking {
        // La regle la plus couteuse a enfreindre du projet : une valeur absente
        // confondue avec zero fausse des mois de journal en silence, et le fausse
        // dans le sens rassurant.
        val diary = journal()

        diary.save(petitDejeuner(LUNDI))

        val ligne = diary.dish(PLAT)!!.entries.first { it.id == SANS_FIBRES }
        assertNull("les fibres inconnues sont devenues un chiffre", ligne.macros.fiber)
        assertEquals(0.0, ligne.macros.sugars!!, 0.0)
    }

    @Test
    fun `les six valeurs d une ligne survivent a l aller-retour`() = runBlocking {
        val diary = journal()
        val attendu = petitDejeuner(LUNDI).entries.first { it.id == LIGNE }

        diary.save(petitDejeuner(LUNDI))

        assertEquals(attendu, diary.dish(PLAT)!!.entries.first { it.id == LIGNE })
    }

    // --- Écrire -----------------------------------------------------------------

    @Test
    fun `reecrire un plat remplace entierement ses lignes`() = runBlocking {
        // Une ligne retiree a l'ecran doit disparaitre. Un rapprochement ligne a
        // ligne laisserait la question ouverte pour chaque cas de figure.
        val diary = journal()
        diary.save(petitDejeuner(LUNDI))

        diary.save(petitDejeuner(LUNDI).let { it.copy(entries = it.entries.take(1)) })

        assertEquals(listOf(LIGNE), diary.dish(PLAT)!!.entries.map { it.id })
    }

    @Test
    fun `reecrire un plat ne touche pas les autres`() = runBlocking {
        val diary = journal()
        diary.save(petitDejeuner(LUNDI))
        diary.save(petitDejeuner(LUNDI, plat = AUTRE_PLAT, loggedAt = SOIR))

        diary.save(petitDejeuner(LUNDI).let { it.copy(entries = it.entries.take(1)) })

        assertEquals(DEUX_LIGNES, diary.dish(AUTRE_PLAT)!!.entries.size)
    }

    @Test
    fun `le titre et le moment d un plat se relisent tels quels`() = runBlocking {
        val diary = journal()

        diary.save(petitDejeuner(LUNDI, title = "Poke bowl", moment = MealMoment.DINNER))

        val relu = diary.dish(PLAT)!!
        assertEquals("Poke bowl", relu.title)
        assertEquals(MealMoment.DINNER, relu.moment)
    }

    @Test
    fun `un plat sans titre ni moment les relit nuls`() = runBlocking {
        // L'etat de tous les plats ecrits avant que les moments existent. Un defaut
        // pose ici -- une chaine vide, un moment de repli -- affirmerait un choix que
        // personne n'a fait, et l'heure du plat ne pourrait plus le corriger.
        val diary = journal()

        diary.save(petitDejeuner(LUNDI))

        val relu = diary.dish(PLAT)!!
        assertNull(relu.title)
        assertNull(relu.moment)
    }

    @Test
    fun `la source et la date d un plat se relisent telles quelles`() = runBlocking {
        // La source est fixee a la creation et ne change jamais, meme apres vingt
        // corrections a la main.
        val diary = journal()

        diary.save(petitDejeuner(LUNDI, source = EntrySource.PHOTO_AI))

        val relu = diary.dish(PLAT)!!
        assertEquals(EntrySource.PHOTO_AI, relu.source)
        assertEquals(LUNDI, relu.date)
    }

    @Test
    fun `les lignes relues sont rattachees a leur plat`() = runBlocking {
        // L'invariant que la fixture de ce contrat a fait apparaitre : `dishId` est
        // redondant avec le plat qui porte la ligne. Le faux ne le lit jamais, la base
        // s'en sert pour rattacher -- donc seule une ligne coherente se relit pareil
        // des deux cotes, et c'est ce que le port doit garantir a ses appelants.
        val diary = journal()
        diary.save(petitDejeuner(LUNDI))
        diary.save(petitDejeuner(LUNDI, plat = AUTRE_PLAT, loggedAt = SOIR))

        val plats = diary.observeDay(LUNDI).first()

        assertTrue(
            "une ligne a change de plat en passant par le port",
            plats.all { plat -> plat.entries.all { it.dishId == plat.id } },
        )
    }

    // --- Supprimer --------------------------------------------------------------

    @Test
    fun `supprimer un plat emporte ses lignes`() = runBlocking {
        // Une ligne orpheline n'a aucune existence dans le domaine. Cote base c'est
        // une cascade ; cote memoire les lignes sont portees par le plat -- deux
        // mecanismes, une seule regle observable.
        val diary = journal()
        diary.save(petitDejeuner(LUNDI))

        diary.deleteDish(PLAT)

        assertNull(diary.dish(PLAT))
        assertTrue(diary.observeDay(LUNDI).first().isEmpty())
    }

    @Test
    fun `supprimer un plat inconnu ne fait rien`() = runBlocking {
        val diary = journal()
        diary.save(petitDejeuner(LUNDI))

        diary.deleteDish(DishId("jamais-ecrit"))

        assertEquals(DEUX_LIGNES, diary.dish(PLAT)!!.entries.size)
    }

    // --- Les flux se démentent --------------------------------------------------

    @Test
    fun `la journee se dement apres une ecriture`() = runBlocking {
        val diary = journal()

        val apres = diary.observeDay(LUNDI).firstAfter(
            write = { diary.save(petitDejeuner(LUNDI)) },
            matching = { it.isNotEmpty() },
        )

        assertEquals(listOf(PLAT), apres.map { it.id })
    }

    @Test
    fun `la journee se dement apres une suppression`() = runBlocking {
        val diary = journal()
        diary.save(petitDejeuner(LUNDI))

        val apres = diary.observeDay(LUNDI).firstAfter(
            write = { diary.deleteDish(PLAT) },
            matching = { it.isEmpty() },
        )

        assertTrue(apres.isEmpty())
    }

    @Test
    fun `la journee se dement quand un plat perd une ligne`() = runBlocking {
        // Le cas que la lecture unique laissait passer ailleurs : retirer une ligne
        // ne change pas la liste des plats, seulement leur contenu. Depuis que le
        // balayage a disparu, c'est `save` qui l'ecrit -- le brouillon relu part sans
        // elle ([D117]).
        val diary = journal()
        val plat = petitDejeuner(LUNDI)
        diary.save(plat)

        val apres = diary.observeDay(LUNDI).firstAfter(
            write = { diary.save(plat.copy(entries = plat.entries.filterNot { it.id == SANS_FIBRES })) },
            matching = { jour -> jour.firstOrNull()?.entries?.size == 1 },
        )

        assertEquals(listOf(LIGNE), apres.single().entries.map { it.id })
    }

    // --- Outillage --------------------------------------------------------------

    /**
     * Deux lignes, dont une **sans valeur de fibres**.
     *
     * Le trou est délibéré et présent dans toutes les fixtures : c'est le cas le plus
     * fréquent des produits emballés, et celui qu'une implémentation distraite
     * comblerait avec un zéro.
     *
     * **Le plat se construit entier, il ne se `copy`e pas.** `FoodEntry.dishId` est
     * redondant avec le plat qui porte la ligne, et les deux implémentations n'en font
     * pas le même usage : le faux l'ignore — ses lignes sont dans le plat — là où Room
     * s'en sert comme clé de rattachement. Un `copy(id = …)` sur un plat produit donc
     * un objet que le faux accepte sans broncher et que la base refuse. C'est ce
     * contrat qui l'a mis au jour, en écrivant précisément cette fixture-là.
     */
    // --- Combien d'entrées citent une fiche -------------------------------------

    @Test
    fun `noter un plat fait monter le compte`() = runBlocking {
        // La propriete que l'ancien faux ne pouvait pas tenir : il lisait une carte
        // posee a la main, que rien n'obligeait a suivre le journal. C'est le cas
        // pour lequel ce port a ete separe du catalogue (D71).
        val ouvert = open()
        ouvert.givenFood(PAIN_COMPLET)

        ouvert.diary.save(repasCitant(LUNDI, listOf(PAIN_COMPLET)))

        assertEquals(1, ouvert.citations.count(PAIN_COMPLET))
    }

    @Test
    fun `deux lignes du meme repas comptent deux`() = runBlocking {
        // Des entrees, pas des plats : « utilisee dans 2 entrees » est ce que la
        // phrase affichee annonce avant une suppression.
        val ouvert = open()
        ouvert.givenFood(PAIN_COMPLET)

        ouvert.diary.save(repasCitant(LUNDI, listOf(PAIN_COMPLET, PAIN_COMPLET)))

        assertEquals(DEUX_LIGNES, ouvert.citations.count(PAIN_COMPLET))
    }

    @Test
    fun `une ligne qui cite une autre fiche ne compte pas`() = runBlocking {
        val ouvert = open()
        ouvert.givenFood(PAIN_COMPLET)
        ouvert.givenFood(SAUCE_TOMATE)

        ouvert.diary.save(repasCitant(LUNDI, listOf(SAUCE_TOMATE)))

        assertEquals(0, ouvert.citations.count(PAIN_COMPLET))
    }

    @Test
    fun `supprimer le plat fait redescendre le compte`() = runBlocking {
        // Le compte se relit, il ne se retient pas : une valeur memorisee au premier
        // appel passerait les quatre cas precedents et mentirait a partir d'ici.
        val ouvert = open()
        ouvert.givenFood(PAIN_COMPLET)
        ouvert.diary.save(repasCitant(LUNDI, listOf(PAIN_COMPLET)))
        assertEquals(1, ouvert.citations.count(PAIN_COMPLET))

        ouvert.diary.deleteDish(PLAT)

        assertEquals(0, ouvert.citations.count(PAIN_COMPLET))
    }

    /**
     * Un repas dont chaque ligne cite la fiche donnée, dans l'ordre.
     *
     * Une liste et non un `vararg` : [FoodId] est une `value class`, que Kotlin
     * refuse en paramètre variadique.
     */
    private fun repasCitant(date: LocalDate, cites: List<FoodId>) = Dish(
        id = PLAT,
        date = date,
        source = EntrySource.MANUAL,
        loggedAt = MATIN,
        entries = cites.mapIndexed { rang, fiche ->
            FoodEntry(
                id = EntryId("${PLAT.value}-ligne$rang"),
                dishId = PLAT,
                foodId = fiche,
                displayName = "Ligne $rang",
                quantity = 100.0,
                unit = "g",
                grams = 100.0,
                macros = Macros(kcal = 100.0, protein = 1.0, carbs = 1.0, sugars = 1.0, fat = 1.0, fiber = 1.0),
            )
        },
    )

    private fun petitDejeuner(
        date: LocalDate,
        plat: DishId = PLAT,
        loggedAt: Instant = MATIN,
        source: EntrySource = EntrySource.MANUAL,
        title: String? = null,
        moment: MealMoment? = null,
    ) = Dish(
        id = plat,
        date = date,
        source = source,
        loggedAt = loggedAt,
        title = title,
        moment = moment,
        entries = listOf(
            FoodEntry(
                id = EntryId("${plat.value}$PAIN"),
                dishId = plat,
                displayName = "Pain complet",
                quantity = 80.0,
                unit = "g",
                grams = 80.0,
                macros = Macros(kcal = 198.0, protein = 7.8, carbs = 36.0, sugars = 2.1, fat = 1.6, fiber = 5.4),
            ),
            FoodEntry(
                id = EntryId("${plat.value}$SAUCE"),
                dishId = plat,
                displayName = "Sauce tomate cuisinee",
                quantity = 60.0,
                unit = "g",
                grams = 60.0,
                // Sucres a zero et fibres inconnues, cote a cote : c'est la paire qui
                // rend la confusion visible si elle a lieu.
                macros = Macros(kcal = 41.0, protein = 1.0, carbs = 5.4, sugars = 0.0, fat = 1.6, fiber = null),
            ),
        ),
    )

    protected companion object {
        val LUNDI: LocalDate = LocalDate.of(2026, 8, 10)
        val MARDI: LocalDate = LocalDate.of(2026, 8, 11)

        val MATIN: Instant = Instant.parse("2026-08-10T08:00:00Z")
        val SOIR: Instant = Instant.parse("2026-08-10T20:00:00Z")

        val PLAT = DishId("plat-matin")
        val AUTRE_PLAT = DishId("plat-soir")

        // Les identifiants de ligne derivent de celui du plat : deux plats ecrits
        // dans la meme base ne peuvent pas se disputer une cle primaire.
        const val PAIN = "-pain"
        const val SAUCE = "-sauce"
        val LIGNE = EntryId("${PLAT.value}$PAIN")
        val SANS_FIBRES = EntryId("${PLAT.value}$SAUCE")

        val PAIN_COMPLET = FoodId("fiche-pain-complet")
        val SAUCE_TOMATE = FoodId("fiche-sauce-tomate")

        const val DEUX_LIGNES = 2
    }
}
