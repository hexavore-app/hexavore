package app.hexavore.feature.entry

import app.hexavore.domain.diary.DraftLineId
import app.hexavore.domain.diary.QuantityUnit
import app.hexavore.domain.diary.Suggestion
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.NutrientValues
import app.hexavore.domain.resolution.MatchVerdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * La frontière entre le texte des champs et les nombres du domaine.
 *
 * C'est le seul endroit du parcours de saisie où « inconnu » peut se transformer en
 * « zéro » sans que rien ne le signale : il suffirait de lire un champ vide comme un
 * `0` pour éviter tout traitement du cas nul, et le journal porterait alors des
 * zéros que personne n'a saisis.
 */
class EntryFormTest {
    @Test
    fun `un champ de macro laisse vide veut dire inconnu et non zero`() {
        val line = ligne(kcal = "195", protein = "4", fiber = "")

        val values = line.toDraftLine().values

        assertEquals(4.0, values.protein)
        assertNull(values.fiber, "un champ vide n'est pas zero gramme de fibres")
    }

    @Test
    fun `sans energie la ligne n est pas enregistrable, mais garde ce qu on sait d elle`() {
        // Une ligne sans energie n'est pas une ligne a zero calorie : c'est une
        // ligne qu'on ne peut pas encore enregistrer. Les valeurs deja connues
        // restent -- c'est ce qui permet a un aliment sans energie determinee, la
        // feta ou les capres, d'arriver avec ses proteines.
        val line = ligne(kcal = "", protein = "4")

        assertNull(line.toDraftLine().values.kcal)
        assertEquals(4.0, line.toDraftLine().values.protein)
        assertFalse(line.toDraftLine().complete)
    }

    @Test
    fun `la virgule decimale est acceptee comme le point`() {
        // Le clavier decimal d'un telephone en francais produit une virgule.
        // La refuser rendrait la saisie impossible sans qu'aucun message ne dise
        // pourquoi.
        assertEquals(12.5, ligne(quantity = "12,5").toDraftLine().quantity)
        assertEquals(12.5, ligne(quantity = "12.5").toDraftLine().quantity)
    }

    @Test
    fun `une ligne complete est enregistrable`() {
        assertTrue(ligne().toDraftLine().complete)
    }

    @Test
    fun `une quantite nulle ne suffit pas`() {
        // Zero gramme de riz n'est pas un repas, c'est une ligne oubliee.
        assertFalse(ligne(quantity = "0").toDraftLine().complete)
    }

    @Test
    fun `un nom fait de blancs ne suffit pas`() {
        assertFalse(ligne(name = "   ").toDraftLine().complete)
    }

    @Test
    fun `une valeur entiere se relit sans decimale`() {
        // « 150.0 » donnerait a chaque relecture d'un plat l'apparence d'une
        // precision au dixieme de gramme que personne n'a saisie.
        val relu = EntryFormLine.of(ligne(quantity = "150").toDraftLine())

        assertEquals("150", relu.quantity)
    }

    @Test
    fun `une valeur decimale garde sa decimale a la relecture, avec le separateur de la langue`() {
        // « 12.5 » en francais etait un `toString()` (D173).
        assertEquals("12,5", enLangue(Locale.FRENCH) { relue("12,5") })
        assertEquals("12.5", enLangue(Locale.ENGLISH) { relue("12,5") })
    }

    @Test
    fun `une demi-portion se relit sans arrondi`() {
        assertEquals("0,25", enLangue(Locale.FRENCH) { relue("0,25") })
    }

    @Test
    fun `une portion nommee survit a l aller-retour`() {
        // Le poids voyage avec l'unite : sans lui, rouvrir un plat demanderait de
        // retrouver une fiche peut-etre supprimee pour savoir ce que pesait
        // « 1 tranche » ce jour-la.
        val portion = QuantityUnit.Serving("1 tranche", gramsPerUnit = 30.0)
        val relu = EntryFormLine.of(ligne(unit = portion, quantity = "2").toDraftLine())

        assertEquals(portion, relu.unit)
    }

    @Test
    fun `ce qui manque a une ligne se nomme, dans l ordre de l ecran`() {
        // « Un champ est vide » n'aide personne devant vingt-quatre champs. L'ordre
        // est celui de l'ecran, de haut en bas : on designe le premier manque, parce
        // qu'en signaler trois ne dit plus par ou commencer.
        assertEquals(MissingField.NAME, ligne(name = "").missing)
        assertEquals(MissingField.QUANTITY, ligne(quantity = "").missing)
        assertEquals(MissingField.CALORIES, ligne(kcal = "").missing)
        assertNull(ligne().missing, "une ligne complete ne manque de rien")
    }

    @Test
    fun `une quantite nulle manque autant qu une quantite absente`() {
        // Zero gramme de riz n'est pas une saisie : c'est une ligne qu'on n'a pas
        // finie.
        assertEquals(MissingField.QUANTITY, ligne(quantity = "0").missing)
    }

    @Test
    fun `une unite choisie survit a l aller-retour`() {
        val relu = EntryFormLine.of(ligne(unit = QuantityUnit.Millilitre).toDraftLine())

        assertEquals(QuantityUnit.Millilitre, relu.unit)
    }

    @Test
    fun `choisir une alternative garde la quantite et abandonne la proposition`() {
        // Celui qui corrige « riz » en « riz complet » ne veut pas retaper 180 g. Et
        // une ligne qu'on vient de choisir n'est plus une proposition a relire : la
        // marque tombe avec les alternatives.
        val line = ligne(quantity = "180").copy(
            suggestion = Suggestion(
                confidence = 0.9f,
                verdict = MatchVerdict.REVIEW,
                alternatives = listOf(RIZ_COMPLET),
                estimated = true,
            ),
        )

        val chosen = line.apply(LineEdit.Substitute(RIZ_COMPLET))

        assertEquals("Riz complet cuit", chosen.name)
        assertEquals("180", chosen.quantity)
        assertEquals(QuantityUnit.Gram, chosen.unit)
        // 111 kcal pour 100 g, ramenes a 180 g : les valeurs suivent la fiche choisie.
        assertEquals("200", chosen.macros[Macro.CALORIES])
        assertNull(chosen.suggestion, "la ligne est desormais un choix, pas une proposition")
    }

    @Test
    fun `choisir une alternative fait revivre toute la ligne`() {
        // Le defaut rapporte a l'usage : les pastilles disparaissaient, et le nom
        // restait celui d'avant. Un champ de saisie ne relit son texte initial qu'a la
        // premiere composition (D45) ; c'est ce compteur qui lui dit qu'il en commence
        // une nouvelle -- et il doit porter **toute** la ligne, pas ses seules valeurs.
        val line = ligne(quantity = "180")

        val chosen = line.apply(LineEdit.Substitute(RIZ_COMPLET))

        assertEquals(line.substitutions + 1, chosen.substitutions)
    }

    @Test
    fun `taper une quantite ne fait pas revivre le nom`() {
        // La contrepartie, et c'est pour elle que les deux compteurs existent : un
        // recalcul reconstruit les valeurs, jamais le champ qu'on est en train de
        // remplir.
        val line = ligne(quantity = "180")

        val remesuree = line.apply(LineEdit.Quantity("200"))

        assertEquals(line.substitutions, remesuree.substitutions)
    }

    @Test
    fun `choisir une alternative fait revivre les champs`() {
        // Sans ce compteur, le brouillon changerait sans que l'ecran bouge : un champ
        // de saisie ne relit son texte initial qu'a la premiere composition (D45).
        val line = ligne(quantity = "180")

        assertEquals(line.revision + 1, line.apply(LineEdit.Substitute(RIZ_COMPLET)).revision)
    }

    /** La quantité d'une ligne telle qu'un champ la relit, une fois passée par le brouillon. */
    private fun relue(quantite: String): String = EntryFormLine.of(ligne(quantity = quantite).toDraftLine()).quantity

    private fun ligne(
        name: String = "Riz",
        quantity: String = "150",
        unit: QuantityUnit = QuantityUnit.Gram,
        kcal: String = "195",
        protein: String = "4",
        fiber: String = "1,2",
    ) = EntryFormLine(
        id = DraftLineId("l1"),
        name = name,
        quantity = quantity,
        unit = unit,
        macros = mapOf(
            Macro.CALORIES to kcal,
            Macro.PROTEIN to protein,
            Macro.FIBER to fiber,
        ),
    )

    private companion object {
        val RIZ_COMPLET = Food(
            id = FoodId("f-riz-complet"),
            source = FoodSource.CIQUAL,
            name = "Riz complet cuit",
            per100g = NutrientValues(kcal = 111.0),
        )
    }
}

/** La langue de l'application, le temps d'un bloc : c'est elle que suit le texte d'un champ. */
private fun <T> enLangue(locale: Locale, block: () -> T): T {
    val avant = Locale.getDefault()
    Locale.setDefault(locale)
    try {
        return block()
    } finally {
        Locale.setDefault(avant)
    }
}
