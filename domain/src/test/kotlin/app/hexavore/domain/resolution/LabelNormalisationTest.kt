package app.hexavore.domain.resolution

import app.hexavore.domain.language.ContentLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * L'étape 1 de [docs/04][sources], et la ligne de partage entre ses deux moitiés.
 *
 * Casse, accents et ponctuation ne sont pas éprouvés ici : ils appartiennent à
 * `SearchText`, qui a ses propres cas. Ce qui se juge ici est ce que la résolution
 * ajoute — les articles, qui partent tout de suite parce qu'une recherche
 * conjonctive ne rendrait rien avec eux, et les pluriels, qui **ne partent pas**
 * parce que l'index de l'ANSES garde les siens.
 *
 * **Chaque règle est éprouvée dans les deux langues**, et pas par symétrie : ce sont deux
 * tables distinctes, et une langue ajoutée sans la sienne ne compile pas. Ce que ces cas
 * vérifient est qu'aucune des deux ne s'applique à l'autre — un article français retiré
 * d'un libellé anglais viderait la requête.
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
class LabelNormalisationTest {
    @Test
    fun `le libelle passe par la normalisation de l index`() {
        assertEquals("jus d orange", normaliseLabel("Jus d'Orange", FR))
    }

    @Test
    fun `un article de tete part, parce qu une recherche conjonctive ne le pardonne pas`() {
        // « du pain » ne rend rien : le catalogue compare une sous-chaine entiere, et
        // la table de l'ANSES exige que tous les termes soient presents.
        assertEquals("pain", normaliseLabel("du pain", FR))
        assertEquals("confiture", normaliseLabel("de la confiture", FR))
        assertEquals("oeuf", normaliseLabel("un œuf", FR))
        assertEquals("huile d olive", normaliseLabel("de l'huile d'olive", FR))
    }

    @Test
    fun `le meme mot reste quand il n est pas en tete`() {
        assertEquals("pain de mie", normaliseLabel("pain de mie", FR))
        assertEquals("blanc de poulet", normaliseLabel("blanc de poulet", FR))
    }

    @Test
    fun `un libelle qui n est fait que d articles ne laisse rien a chercher`() {
        assertEquals("", normaliseLabel("de la", FR))
    }

    @Test
    fun `la normalisation ne touche pas aux pluriels`() {
        // C'est la moitie de l'etape 1 qui n'a pas lieu ici : l'index de l'ANSES
        // garde ses pluriels, et « haricots verts » se trouve tel quel.
        assertEquals("haricots verts", normaliseLabel("des haricots verts", FR))
    }

    @Test
    fun `le pluriel naif retire le s et le x`() {
        assertEquals("pomme", depluralise("pommes", FR))
        assertEquals("chou", depluralise("choux", FR))
    }

    @Test
    fun `la terminaison en aux redevient al, et passe avant la regle du x`() {
        assertEquals("cheval", depluralise("chevaux", FR))
    }

    @Test
    fun `chaque mot est traite, pas seulement le dernier`() {
        assertEquals("haricot vert", depluralise("haricots verts", FR))
    }

    @Test
    fun `un mot court reste entier`() {
        // « jus » n'est pas un pluriel, et « ju » ne designerait plus rien. La regle
        // etant naive, c'est la garde qui la rend supportable — avec l'ordre des
        // requetes, qui fait que ce libelle n'arrive jamais ici.
        assertEquals("jus", depluralise("jus", FR))
    }

    // --- Anglais -------------------------------------------------------------------

    @Test
    fun `les articles anglais partent, et les mots de quantite avec eux`() {
        assertEquals("bread", normaliseLabel("the bread", EN))
        assertEquals("apple", normaliseLabel("an apple", EN))
        // Le modele ecrit volontiers « a glass of milk » : « a » et « of » sont en tete
        // l'un apres l'autre, et `dropWhile` les emporte tous les deux.
        assertEquals("glass of milk", normaliseLabel("a glass of milk", EN))
        assertEquals("bread", normaliseLabel("some bread", EN))
    }

    @Test
    fun `un article francais n est pas un article anglais, et reciproquement`() {
        // C'est le defaut que deux tables distinctes evitent : « the » garde son sens de
        // designation en francais -- il n'y en a aucun -- donc il reste, et « du » reste
        // en anglais. Les melanger aurait vide des requetes.
        assertEquals("the bread", normaliseLabel("the bread", FR))
        assertEquals("du pain", normaliseLabel("du pain", EN))
    }

    @Test
    fun `le meme mot anglais reste quand il n est pas en tete`() {
        assertEquals("cream of mushroom soup", normaliseLabel("cream of mushroom soup", EN))
    }

    @Test
    fun `le pluriel anglais retire le s`() {
        assertEquals("apple", depluralise("apples", EN))
        assertEquals("carrot", depluralise("carrots", EN))
    }

    @Test
    fun `les finales en ies redeviennent y, et passent avant la regle du es`() {
        assertEquals("berry", depluralise("berries", EN))
    }

    @Test
    fun `les sifflantes perdent leur es, et non leur s seul`() {
        assertEquals("dish", depluralise("dishes", EN))
        assertEquals("box", depluralise("boxes", EN))
        assertEquals("tomato", depluralise("tomatoes", EN))
    }

    @Test
    fun `un mot en double s n est pas un pluriel`() {
        // La seule garde qui compte vraiment : « glass », « cress » et « bass » sont des
        // aliments, ce ne sont pas des pluriels, et un `-s` retire a l'aveugle en aurait
        // fait trois requetes vides.
        assertEquals("glass", depluralise("glass", EN))
        assertEquals("cress", depluralise("cress", EN))
        assertEquals("bass", depluralise("bass", EN))
    }

    @Test
    fun `chaque mot anglais est traite, pas seulement le dernier`() {
        assertEquals("green bean", depluralise("greens beans", EN))
    }

    private companion object {
        val FR = ContentLanguage.FRENCH
        val EN = ContentLanguage.ENGLISH
    }
}
