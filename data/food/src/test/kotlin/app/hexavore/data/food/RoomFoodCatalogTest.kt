package app.hexavore.data.food

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.hexavore.core.database.HexavoreDatabase
import app.hexavore.core.database.ciqual.CiqualDatabase
import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.FixedLanguage
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.core.testing.TestDispatchers
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.language.ContentLanguage
import app.hexavore.domain.nutrition.Macro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Le contrat du catalogue, joué sur Room et sur la table de l'ANSES **livrée**.
 *
 * C'est le côté qui manquait. Les tests de la tranche 3 ne connaissaient que le faux,
 * qui rendait des fiches déjà écrites ; celui-ci fabrique des fiches qui ne sont pas
 * encore au catalogue, avec des identifiants provisoires — et c'est exactement là que
 * deux défauts sont passés ([D53][decisions]).
 *
 * Sous Robolectric, donc sans appareil : un test qu'il faut brancher un téléphone
 * pour exécuter est un test qu'on n'exécute pas ([D35][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class RoomFoodCatalogTest : FoodCatalogContract() {
    private val bases = mutableListOf<HexavoreDatabase>()

    @After
    fun fermer() = bases.forEach { it.close() }

    override fun catalogue(stored: List<Food>, reference: List<Food>): FoodCatalogView<*> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val base = Room
            .inMemoryDatabaseBuilder(context, HexavoreDatabase::class.java)
            .build()
            .also(bases::add)

        val ciqual = CiqualDatabase(context)
        reference.forEach { ciqual.verifier(it) }

        val catalogue = RoomFoodCatalog(
            dao = base.foodDao(),
            marks = base.foodMarksDao(),
            ciqual = ciqual,
            // Le francais : les fiches de reference de ce fichier portent les libelles
            // de l'ANSES en francais, et c'est sur eux que le contrat compare.
            languages = FixedLanguage(),
            ids = SequentialIdGenerator("provisoire"),
            clock = FixedClock(MAINTENANT),
            dispatchers = TestDispatchers(Dispatchers.IO),
        )
        runBlocking { stored.forEach { catalogue.save(it) } }
        return FoodCatalogView(catalogue, RoomBarcodeLookup(base.foodDao(), TestDispatchers(Dispatchers.IO)))
    }

    /**
     * La table de l'ANSES n'est pas garnissable : elle est livrée telle quelle.
     *
     * Ce contrôle est ce qui empêche le contrat de ne rien éprouver de ce côté. Sans
     * lui, une fiche de référence absente de la base rendrait simplement zéro
     * résultat, et la moitié des cas passeraient en ne mesurant rien — le genre de
     * vert qui a déjà coûté deux corrections.
     */
    private fun CiqualDatabase.verifier(attendu: Food) {
        val code = checkNotNull(attendu.sourceRef) { "une fiche de reference porte un code CIQUAL" }
        val ligne = byCode(code, ContentLanguage.FRENCH.tag)
        checkNotNull(ligne) { "le code $code n'est plus dans la table livree : la fixture est perimee" }
        check(ligne.name == attendu.name) {
            "l intitule du code $code a change : attendu [${attendu.name}], lu [${ligne.name}]"
        }
    }

    @Test
    fun `la table livree contient bien ce que le contrat lui demande`() {
        // Une sonde, pas un doublon : elle nomme la seule facon dont ce fichier peut
        // devenir un decor vide, et elle echoue avec le code fautif.
        catalogue(reference = listOf(POMME_DE_REFERENCE, POIRE_DE_REFERENCE, CREME_BRULEE))
    }

    /**
     * Le titre court, depuis le CSV versionné jusqu'au modèle de domaine.
     *
     * **La couture entière**, et c'est là qu'elle se romprait sans qu'un test de
     * module ne le voie : `short-names.csv` est lu par une tâche Gradle, écrit dans
     * une colonne de `ciqual.db`, relu par `CiqualDatabase`, puis porté par `Food`.
     * Chaque morceau a son test ; celui-ci est le seul qui parte du fichier livré.
     *
     * Le code choisi est l'une des six lignes écrites à la main, donc il survit à la
     * génération — qui ne redemande que les codes absents du fichier.
     */
    @Test
    fun `un titre court du fichier livre remonte jusqu au modele`() {
        val ligne = livree(CODE_AVEC_TITRE)

        checkNotNull(ligne) { "le code $CODE_AVEC_TITRE n'est plus dans la table livree : la fixture est perimee" }
        assertEquals("Cuisse de poulet rotie", ligne.shortName)
        assertEquals("le libelle d'origine ne bouge pas", "Poulet, cuisse, viande rôtie/cuite au four", ligne.name)
    }

    /**
     * Un libellé déjà lisible n'a pas de titre court, et ce n'est pas un trou.
     *
     * Sans ce cas, une lecture qui rendrait toujours la même chaîne — le libellé, par
     * exemple — passerait le test précédent sans rien mesurer.
     */
    @Test
    fun `une fiche sans titre court en rend aucun`() {
        val ligne = livree(CODE_SANS_TITRE)

        checkNotNull(ligne) { "le code $CODE_SANS_TITRE n'est plus dans la table livree : la fixture est perimee" }
        assertNull(ligne.shortName)
    }

    /**
     * Une teneur complétée, depuis le CSV versionné jusqu'au modèle de domaine.
     *
     * **La couture entière de la seconde passe.** `completions.csv` est lu par une
     * tâche Gradle, écrit dans une colonne **distincte** de `ciqual.db`, relu, puis
     * fondu par le mapper — qui préfère l'originale et retient laquelle a servi.
     *
     * Le code choisi est l'une des deux lignes écrites à la main : les câpres au
     * vinaigre, dont l'ANSES ne détermine pas l'énergie.
     */
    @Test
    fun `une teneur completee du fichier livre remonte marquee`() {
        val ligne = livree(CODE_COMPLETE)

        checkNotNull(ligne) { "le code $CODE_COMPLETE n'est plus dans la table livree : la fixture est perimee" }
        assertNull("l ANSES ne determine pas cette energie", ligne.kcal100)
        assertEquals(39.0, ligne.estimated.kcal100)

        val food = ligne.toDomain(FoodId("provisoire"), emptyList())
        assertEquals("la completion comble le trou a l affichage", 39.0, food.per100g.kcal)
        assertEquals("et la fiche dit d ou elle vient", setOf(Macro.CALORIES), food.estimated)
    }

    /**
     * Ce que l'ANSES publie n'est jamais marqué, et jamais remplacé.
     *
     * Sans ce cas, un mapper qui marquerait tout, ou qui préférerait la complétion,
     * passerait le test précédent sans rien mesurer.
     */
    @Test
    fun `une teneur mesuree n est ni marquee ni remplacee`() {
        val ligne = livree(CODE_COMPLETE)!!

        val food = ligne.toDomain(FoodId("provisoire"), emptyList())

        assertEquals("les fibres sont mesurees", ligne.fiber100, food.per100g.fiber)
        assertEquals("et rien d autre que l energie n est marque", setOf(Macro.CALORIES), food.estimated)
    }

    /**
     * La table livrée, interrogée en français.
     *
     * Une fonction plutôt que la ligne écrite six fois : elle nomme ce que ces cas ont en
     * commun — la base de l'APK, et la langue dans laquelle les fixtures sont écrites.
     */
    private fun livree(code: String, language: ContentLanguage = ContentLanguage.FRENCH) =
        CiqualDatabase(ApplicationProvider.getApplicationContext()).byCode(code, language.tag)

    /**
     * La même fiche, dans les deux langues, depuis la base livrée dans l'APK.
     *
     * **La couture que rien d'autre n'éprouve.** `alim_nom_eng` est lu par une tâche
     * Gradle, écrit dans une seconde ligne de `ciqual_name`, et relu ici par le code qui
     * tourne sur le téléphone. Sans ce cas, une colonne oubliée à l'import rendrait
     * simplement zéro résultat en anglais — et personne ne le verrait avant d'installer
     * l'application dans cette langue.
     *
     * Les deux libellés sont affirmés en dur : c'est ce qui fait échouer ce cas le jour où
     * l'ANSES republie en renommant l'un des deux, plutôt que de le laisser passer.
     */
    @Test
    fun `une fiche livree se lit dans les deux langues, avec les memes teneurs`() {
        val francais = livree(CODE_SANS_TITRE)
        val anglais = livree(CODE_SANS_TITRE, ContentLanguage.ENGLISH)

        checkNotNull(francais) { "le code $CODE_SANS_TITRE n'est plus dans la table livree" }
        checkNotNull(anglais) { "la table livree ne porte pas l'anglais : l'import a-t-il tourne ?" }
        assertEquals("Carotte, crue", francais.name)
        assertEquals("Carrot, raw", anglais.name)
        assertEquals("les teneurs ne dependent d aucune langue", francais.kcal100, anglais.kcal100)
        assertEquals("ni le rayon", francais.category, anglais.category)
    }

    /**
     * Le rayon de l'ANSES est traduit, et c'est ce qui départage deux homonymes.
     *
     * Il s'affiche sous le nom dans la liste de résultats : le laisser en français sous un
     * libellé anglais aurait été le détail qui trahit une traduction à moitié faite.
     */
    @Test
    fun `le rayon d une fiche livree suit la langue`() {
        assertEquals("légumes", livree(CODE_SANS_TITRE)?.groupName)
        assertEquals("vegetables", livree(CODE_SANS_TITRE, ContentLanguage.ENGLISH)?.groupName)
    }

    /**
     * Les portions nommées suivent la langue, et gardent leur poids.
     *
     * Le poids est écrit une fois dans `servings.csv` : c'est ce qui rend impossible que
     * « 1 pomme moyenne » et « 1 medium apple » finissent par ne plus peser la même chose.
     */
    @Test
    fun `une portion livree se lit dans les deux langues, pour un meme poids`() {
        val ciqual = CiqualDatabase(ApplicationProvider.getApplicationContext())
        val francaises = ciqual.servings(CODE_AVEC_PORTION, ContentLanguage.FRENCH.tag)
        val anglaises = ciqual.servings(CODE_AVEC_PORTION, ContentLanguage.ENGLISH.tag)

        assertEquals("1 pomme moyenne", francaises.single().label)
        assertEquals("1 medium apple", anglaises.single().label)
        assertEquals(francaises.single().grams, anglaises.single().grams, 0.0)
    }

    private companion object {
        val MAINTENANT: Instant = Instant.parse("2026-08-10T10:00:00Z")

        /** « Pomme, chair et peau, crue » : une portion nommée, écrite à la main. */
        const val CODE_AVEC_PORTION = "13039"

        /** « Câpres, au vinaigre » : sans énergie déterminée, complétée à la main. */
        const val CODE_COMPLETE = "11040"

        /** « Poulet, cuisse, viande rôtie/cuite au four », l'une des six lignes écrites à la main. */
        const val CODE_AVEC_TITRE = "36006"

        /** « Carotte, crue » : treize caractères, il n'y a rien à raccourcir. */
        const val CODE_SANS_TITRE = "20009"
    }
}

/**
 * Version d'Android simulée par Robolectric.
 *
 * Choisie parmi celles que la version de Robolectric embarque, et non alignée sur
 * `compileSdk` : ces tests portent sur SQLite et sur des flux, pas sur une API dont
 * la version importerait.
 */
internal const val ROBOLECTRIC_SDK = 33
