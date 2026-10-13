package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemorySelectedDay
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.EstimatedFood
import app.hexavore.domain.ai.EstimatedUnit
import app.hexavore.domain.ai.EstimationOutcome
import app.hexavore.domain.ai.Recognition
import app.hexavore.domain.ai.RecognizedItem
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.QuantityUnit
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodFilter
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodSearch
import app.hexavore.domain.food.FoodServing
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.nutrition.NutrientValues
import app.hexavore.domain.resolution.MatchVerdict
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Le chaînage : reconnaître, résoudre, convertir, remplir.
 *
 * Ce qui se juge ici est **l'assemblage**, pas les pièces. La recherche de candidats, le
 * score, le verdict et la conversion ont chacun leurs cas ailleurs ; les rejouer ici
 * ferait deux jeux à corriger pour une règle. Ce qui n'existe qu'ici : l'ordre des deux
 * étapes, le sort d'un libellé introuvable, et ce que la ligne garde de la proposition.
 */
class ResolveRecognitionTest {
    private val ids = SequentialIdGenerator()

    @Test
    fun `chaque ligne reconnue devient une ligne de brouillon`() = runTest {
        val draft = resolve(item("pomme", 2.0, EstimatedUnit.PIECE), item("pain de mie", 1.0, EstimatedUnit.SLICE))

        assertEquals(listOf("Pomme", "Pain de mie"), draft.lines.map { it.name })
        assertEquals(EntrySource.TEXT_AI, draft.source)
    }

    @Test
    fun `la portion nommee de la fiche l emporte sur le forfait`() = runTest {
        // La regle de D73, vue depuis le chainage : la fiche dit qu'une tranche pese
        // 33 g, et le forfait de docs/04 en dirait 30. Convertir avant de savoir
        // quelle fiche on vise appliquerait le forfait a tous les coups.
        val draft = resolve(item("pain de mie", 2.0, EstimatedUnit.SLICE))

        assertEquals(66.0, draft.lines.single().quantity)
        assertEquals(QuantityUnit.Gram, draft.lines.single().unit)
        assertFalse(draft.lines.single().suggestion!!.estimated, "la fiche a mesure, rien n a ete devine")
    }

    @Test
    fun `cinq cacahuetes pesent ce que le modele dit, pas cinq cents grammes`() = runTest {
        // Le defaut qui a motive ce cas : « une piece » n implique aucune taille, et le
        // forfait de cent grammes faisait cinq cents grammes de cinq cacahuetes.
        val draft = resolve(item("cacahuete", 5.0, EstimatedUnit.PIECE, grams = 4.0))

        assertEquals(4.0, draft.lines.single().quantity)
        assertTrue(draft.lines.single().suggestion!!.estimated, "il a estime, il n a pas pese")
    }

    @Test
    fun `une ligne en grammes prend le poids du modele, pas sa part`() = runTest {
        // Le signalement qui a ouvert D155 : « chou-fleur, 1 G, grams 30 » entrait au
        // journal a un gramme et zero kilocalorie. Le modele avait compte une part la
        // ou le champ attendait un poids, et « un gramme vaut un gramme » multipliait
        // sa part par un.
        val draft = resolve(item("pomme", 1.0, EstimatedUnit.G, grams = 30.0))

        assertEquals(30.0, draft.lines.single().quantity)
        assertFalse(draft.lines.single().suggestion!!.estimated, "un gramme ne se devine pas")
    }

    @Test
    fun `un aliment reellement a un gramme reste signale`() = runTest {
        // Le garde-fou de D138 ne bouge pas : quand les deux champs s accordent sur un
        // gramme, c est bien un gramme qui entre -- et l oeil doit aller dessus.
        val draft = resolve(item("pomme", 1.0, EstimatedUnit.G, grams = 1.0))

        assertEquals(1.0, draft.lines.single().quantity)
        assertTrue(draft.lines.single().suggestion!!.estimated, "une pomme a un gramme n existe pas")
    }

    @Test
    fun `le poids du modele ne prend pas le pas sur la fiche`() = runTest {
        // La fiche dit qu une tranche pese 33 g : c est une mesure, et une estimation
        // ne l ecrase pas -- meme quand elle vient de qui a vu l assiette.
        val draft = resolve(item("pain de mie", 2.0, EstimatedUnit.SLICE, grams = 100.0))

        assertEquals(66.0, draft.lines.single().quantity)
    }

    @Test
    fun `une conversion au forfait se signale`() = runTest {
        // « un bol » de riz : aucune portion nommee sur la fiche, donc le forfait --
        // et docs/04 exige que la ligne le dise.
        val draft = resolve(item("riz", 1.0, EstimatedUnit.BOWL))

        assertTrue(draft.lines.single().suggestion!!.estimated)
    }

    @Test
    fun `un libelle introuvable garde son nom et sa quantite, sans valeurs`() = runTest {
        // L'ecarter ferait disparaitre un aliment que l'utilisateur a mange, et il ne
        // saurait pas lequel. La ligne arrive incomplete : l'ecran de validation dit
        // deja qu'une ligne sans energie n'est pas enregistrable.
        val draft = resolve(item("tofu fume au sesame", 100.0, EstimatedUnit.G))

        val line = draft.lines.single()
        assertEquals("tofu fume au sesame", line.name)
        assertEquals(100.0, line.quantity)
        assertNull(line.values.kcal)
        assertNull(line.foodId)
    }

    @Test
    fun `les valeurs suivent la quantite reconnue`() = runTest {
        // 200 g d'une fiche a 52 kcal pour 100 g. Sans cette regle de trois, la ligne
        // porterait les valeurs de la portion par defaut de la fiche, qui n'a rien a
        // voir avec ce que le modele a vu.
        val draft = resolve(item("pomme", 200.0, EstimatedUnit.G))

        assertEquals(104.0, draft.lines.single().values.kcal)
    }

    @Test
    fun `la ligne garde les deux incertitudes separement`() = runTest {
        // Le modele est sur de lui, le catalogue beaucoup moins -- il n'a que des
        // preparations, aucune fiche generique. Les moyenner ferait disparaitre le
        // seul cas qui compte, celui ou l'une des deux doute.
        val draft = resolve(item("pomme de terre", 1.0, EstimatedUnit.PIECE, confidence = 0.95f))

        val suggestion = draft.lines.single().suggestion!!
        assertEquals(0.95f, suggestion.confidence)
        assertEquals(MatchVerdict.REVIEW, suggestion.verdict)
        assertEquals(listOf("Pomme de terre frite au four"), suggestion.alternatives.map { it.name })
    }

    @Test
    fun `une correspondance certaine ne propose aucune alternative`() = runTest {
        val draft = resolve(item("pomme", 1.0, EstimatedUnit.PIECE))

        val suggestion = draft.lines.single().suggestion!!
        assertEquals(MatchVerdict.AUTOMATIC, suggestion.verdict)
        assertEquals(emptyList<Food>(), suggestion.alternatives)
    }

    @Test
    fun `une analyse sans ligne rend quand meme un brouillon saisissable`() = runTest {
        // Le cas ne devrait pas se presenter -- une analyse sans ligne exploitable est
        // une erreur, pas une reussite -- mais un brouillon a zero ligne n'aurait rien
        // a afficher, et l'ecran n'a pas d'etat pour ca.
        val draft = resolve()

        assertEquals(1, draft.lines.size)
        assertTrue(draft.lines.single().blank)
    }

    /** Ce que le repli de l'etape 4 repondra, et ce qu'on lui aura demande. */
    private var estimation: EstimationOutcome = EstimationOutcome.Estimated(emptyList())
    private val demandes = mutableListOf<List<String>>()

    private suspend fun resolve(vararg items: RecognizedItem) = ResolveRecognition(
        resolve = ResolveFoodLabel(CATALOGUE, FRANCAIS),
        create = CreateDraft(FixedClock.atNoon(JOUR), ids, InMemorySelectedDay(FixedClock.atNoon(JOUR).today())),
        estimate = { labels ->
            demandes += labels
            estimation
        },
        languages = FRANCAIS,
    )(Recognition(items.toList()), EntrySource.TEXT_AI)

    @Test
    fun `un libelle resolu ne part pas au repli`() = runTest {
        // Le cas courant, et le plus cher a manquer : un appel de plus se paie, et il
        // ne rendrait rien qu'on n'ait deja.
        resolve(item("pomme", 1.0, EstimatedUnit.PIECE))

        assertEquals(emptyList<List<String>>(), demandes)
    }

    @Test
    fun `les libelles non resolus partent en un seul appel`() = runTest {
        // Un appel par ligne aurait coute trois requetes la ou une suffit.
        resolve(
            item("tofu fume au sesame", 100.0, EstimatedUnit.G),
            item("pomme", 1.0, EstimatedUnit.PIECE),
            item("sauce maison", 30.0, EstimatedUnit.G),
        )

        assertEquals(listOf(listOf("tofu fume au sesame", "sauce maison")), demandes)
    }

    @Test
    fun `une estimation remplit la ligne et se signale`() = runTest {
        estimation = EstimationOutcome.Estimated(
            listOf(EstimatedFood("tofu fume au sesame", NutrientValues(kcal = 180.0, protein = 16.0))),
        )

        val draft = resolve(item("tofu fume au sesame", 200.0, EstimatedUnit.G))

        val line = draft.lines.single()
        // 180 kcal pour 100 g, ramenes a 200 g : la reference est posee, donc la
        // quantite recalcule comme pour une vraie fiche.
        assertEquals(360.0, line.values.kcal)
        assertTrue(line.suggestion!!.estimatedMacros, "un chiffre invente doit le dire")
        assertNull(line.foodId, "une estimation n'est pas une fiche et n'entre pas au catalogue")
    }

    @Test
    fun `une estimation reformulee ne se recolle a rien`() = runTest {
        // Le prompt exige que le libelle soit recopie a l'identique. Un modele qui
        // reformule -- « tofu fume » pour « tofu fume au sesame » -- rend une
        // estimation qu'on ne peut plus rattacher : la remettre sur la ligne la plus
        // proche inventerait un rapprochement que personne n'a demande.
        estimation = EstimationOutcome.Estimated(
            listOf(EstimatedFood("tofu fume", NutrientValues(kcal = 180.0))),
        )

        val draft = resolve(
            item("tofu fume au sesame", 100.0, EstimatedUnit.G),
            item("sauce maison", 30.0, EstimatedUnit.G),
        )

        assertNull(draft.lines.first().values.kcal, "une reformulation ne remplit aucune ligne")
        assertNull(draft.lines.last().values.kcal, "et surtout pas celle d'un autre libelle")
    }

    @Test
    fun `un libelle que le modele ne sait pas estimer reste a completer`() = runTest {
        // Le prompt demande d'omettre plutot que d'inventer : une omission se corrige a
        // la main, un chiffre invente passe inapercu.
        estimation = EstimationOutcome.Estimated(emptyList())

        val line = resolve(item("sauce maison", 30.0, EstimatedUnit.G)).lines.single()

        assertNull(line.values.kcal)
        assertFalse(line.suggestion!!.estimatedMacros)
    }

    @Test
    fun `un repli en echec ne fait rien tomber`() = runTest {
        // Sans cle, sans reseau : la ligne reste telle qu'elle etait, et l'ecran dit
        // deja qu'une ligne sans energie n'est pas enregistrable.
        estimation = EstimationOutcome.Failed(AiError.NoProviderConfigured)

        val line = resolve(item("sauce maison", 30.0, EstimatedUnit.G)).lines.single()

        assertEquals("sauce maison", line.name)
        assertNull(line.values.kcal)
    }

    // --- Le choix du modele -----------------------------------------------------------

    @Test
    fun `une fiche choisie par le modele l emporte sur la recherche`() = runTest {
        // **Le defaut que l'outillage corrige.** « Pomme » trouve POMME par la
        // recherche ; le modele, lui, a vu l'assiette et designe la pomme de terre.
        // C'est lui qui doit gagner -- sans quoi l'outil ne servirait a rien.
        val ligne = resolve(item("pomme", 100.0, EstimatedUnit.G).copy(chosen = POMME_DE_TERRE_VAPEUR))
            .lines
            .single()

        assertEquals(POMME_DE_TERRE_VAPEUR.name, ligne.name)
    }

    @Test
    fun `une fiche choisie ne part pas au repli`() = runTest {
        // Elle est resolue : demander une estimation par-dessus se paierait pour rien.
        resolve(item("chose inconnue", 100.0, EstimatedUnit.G).copy(chosen = RIZ))

        assertEquals(emptyList<List<String>>(), demandes)
    }

    @Test
    fun `une fiche choisie n est pas signalee a relire`() = runTest {
        // Les valeurs viennent de la table de l'ANSES et non du modele : ce n'est pas
        // une estimation, et signaler chaque ligne en permanence ne signalerait rien.
        val ligne = resolve(item("pomme", 100.0, EstimatedUnit.G).copy(chosen = POMME)).lines.single()

        assertEquals(MatchVerdict.AUTOMATIC, ligne.suggestion?.verdict)
        assertEquals(emptyList<Food>(), ligne.suggestion?.alternatives)
    }

    @Test
    fun `la confiance du modele est conservee`() = runTest {
        // Elle ne devient pas 1 sous pretexte qu'il a choisi dans une liste : il a pu
        // choisir le moins mauvais, et c'est ce que l'ecran montre ligne par ligne.
        val ligne = resolve(item("pomme", 100.0, EstimatedUnit.G, confidence = 0.6f).copy(chosen = POMME))
            .lines
            .single()

        assertEquals(0.6f, ligne.suggestion?.confidence)
    }

    @Test
    fun `la portion nommee de la fiche choisie sert a convertir`() = runTest {
        // La conversion reste celle du chemin ordinaire : « une pomme » vaut ce que la
        // fiche dit qu'elle vaut, et non le forfait.
        val ligne = resolve(item("pomme", 1.0, EstimatedUnit.PIECE).copy(chosen = POMME)).lines.single()

        assertEquals(150.0, ligne.quantity)
    }

    private fun item(
        label: String,
        quantity: Double,
        unit: EstimatedUnit,
        confidence: Float = 0.9f,
        grams: Double? = null,
    ) = RecognizedItem(label = label, quantity = quantity, unit = unit, confidence = confidence, grams = grams)

    private companion object {
        val JOUR: LocalDate = LocalDate.of(2026, 3, 14)

        val POMME = ciqual("Pomme", kcal = 52.0, servings = listOf(FoodServing("1 pomme", 150.0)))
        val PAIN = ciqual("Pain de mie", kcal = 270.0, servings = listOf(FoodServing("1 tranche", 33.0)))
        val RIZ = ciqual("Riz blanc cuit", kcal = 130.0)
        val POMME_DE_TERRE_VAPEUR = ciqual("Pomme de terre vapeur", kcal = 85.0)
        val POMME_DE_TERRE_FRITE = ciqual("Pomme de terre frite au four", kcal = 190.0)

        /**
         * Un catalogue qui récite, comme celui de `ResolveFoodLabelTest`.
         *
         * Un faux fidèle chercherait vraiment, donc ce test dépendrait de la règle de
         * correspondance qu'un autre éprouve déjà — et une ligne verte ici ne dirait
         * plus si c'est le chaînage ou la recherche qui fonctionne.
         */
        val CATALOGUE = object : FoodSearch {
            override fun search(query: String, filter: FoodFilter, limit: Int): Flow<List<Food>> = flowOf(
                when (query) {
                    "pomme" -> listOf(POMME)
                    "pain de mie" -> listOf(PAIN)
                    "riz" -> listOf(RIZ)
                    "pomme de terre" -> listOf(POMME_DE_TERRE_VAPEUR, POMME_DE_TERRE_FRITE)
                    else -> emptyList()
                },
            )
        }

        fun ciqual(name: String, kcal: Double, servings: List<FoodServing> = emptyList()) = Food(
            id = FoodId(name),
            source = FoodSource.CIQUAL,
            name = name,
            per100g = NutrientValues(kcal = kcal),
            servings = servings,
        )
    }
}
