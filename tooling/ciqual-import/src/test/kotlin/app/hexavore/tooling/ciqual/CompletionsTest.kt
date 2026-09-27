package app.hexavore.tooling.ciqual

import app.hexavore.domain.nutrition.Macro
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * La complétion des teneurs manquantes, sans réseau.
 *
 * **La règle qui commande tout est éprouvée en premier** : une complétion ne se pose
 * jamais sur une teneur que l'ANSES publie. Ni à la demande — on ne demande que les
 * trous —, ni à la relecture du fichier, qui refuse la ligne, ni au fil du temps,
 * puisqu'une complétion devenue inutile est retirée.
 */
class CompletionsTest {
    @TempDir
    lateinit var directory: Path

    // --- Ce qu'on demande ------------------------------------------------------

    @Test
    fun `seules les teneurs absentes sont demandees`() {
        // Poser la question sur une teneur publiee reviendrait a inviter un modele a
        // contredire une mesure.
        val demandes = mutableListOf<Macro>()
        val completions = Completions({ gaps ->
            gaps.forEach { demandes += it.macro }
            emptyMap()
        })

        completions.generate(listOf(CAPRES), emptyList())

        assertEquals(listOf(Macro.CALORIES), demandes, "seule l energie manque a cette fiche")
    }

    @Test
    fun `une fiche complete n est jamais soumise`() {
        val demandes = mutableListOf<Gap>()
        val completions = Completions({ gaps ->
            demandes += gaps
            emptyMap()
        })

        completions.generate(listOf(CAROTTE), emptyList())

        assertTrue(demandes.isEmpty())
    }

    @Test
    fun `ce qui est deja ecrit n est pas redemande`() {
        val demandes = mutableListOf<Gap>()
        val completions = Completions({ gaps ->
            demandes += gaps
            emptyMap()
        })

        completions.generate(listOf(CAPRES), listOf(CiqualCompletion(CAPRES.code, Macro.CALORIES, 39.0)))

        assertTrue(demandes.isEmpty())
    }

    // --- Ce qu'on accepte ------------------------------------------------------

    @Test
    fun `un couple qu on n a pas demande est ignore`() {
        val completions = Completions({ mapOf(("99999" to Macro.FAT) to 3.0) })

        assertTrue(completions.generate(listOf(CAPRES), emptyList()).isEmpty())
    }

    @Test
    fun `une macro qu on n a pas demandee pour cette fiche est ignoree`() {
        // Le rattachement porte sur le **couple**, pas sur le seul code : un modele
        // qui repond sur les lipides quand on demandait l'energie remplirait
        // autrement une colonne que l'ANSES publie.
        val completions = Completions({ mapOf((CAPRES.code to Macro.FAT) to 3.0) })

        assertTrue(completions.generate(listOf(CAPRES), emptyList()).isEmpty())
    }

    @Test
    fun `une teneur negative est ecartee`() {
        val completions = Completions({ mapOf((CAPRES.code to Macro.CALORIES) to -5.0) })

        assertTrue(completions.generate(listOf(CAPRES), emptyList()).isEmpty())
    }

    @Test
    fun `une teneur nulle est acceptee, parce que zero est une reponse`() {
        // Une huile contient reellement zero gramme de glucides. Refuser ce zero
        // ferait redemander eternellement une valeur que le modele a raison de donner.
        val completions = Completions({ mapOf((HUILE.code to Macro.FIBER) to 0.0) })

        assertEquals(listOf(0.0), completions.generate(listOf(HUILE), emptyList()).map { it.value })
    }

    @Test
    fun `une teneur au-dela de cent grammes est ecartee`() {
        // Cent grammes ne contiennent pas cent-cinquante grammes de lipides : le
        // modele s'est trompe d'unite, et la valeur ne se corrige pas, elle se refuse.
        val completions = Completions({ mapOf((HUILE.code to Macro.FIBER) to 150.0) })

        assertTrue(completions.generate(listOf(HUILE), emptyList()).isEmpty())
    }

    @Test
    fun `une energie de neuf cents kilocalories est acceptee`() {
        // Le plafond des grammes ne vaut pas pour l'energie : cent grammes d'huile
        // pure valent bien 900 kcal, et les refuser eliminerait le cas le plus dense
        // de la table.
        val completions = Completions({ mapOf((HUILE_SANS_ENERGIE.code to Macro.CALORIES) to 900.0) })

        assertEquals(
            listOf(900.0),
            completions.generate(listOf(HUILE_SANS_ENERGIE), emptyList()).map { it.value },
        )
    }

    // --- Ce qu'on retire -------------------------------------------------------

    @Test
    fun `une completion devenue inutile est retiree`() {
        // **« La completion est effacee quand la source livre sa mesure. »** Une
        // estimation a ete produite contre un etat precis de la fiche ; l'etat change,
        // elle ne decrit plus cette fiche-la.
        val perimee = CiqualCompletion(CAROTTE.code, Macro.CALORIES, 12.0)

        val gardees = Completions({ emptyMap() }).kept(listOf(CAROTTE), listOf(perimee))

        assertTrue(gardees.isEmpty(), "l ANSES publie desormais cette energie")
    }

    @Test
    fun `une completion qui comble encore un trou est gardee`() {
        val utile = CiqualCompletion(CAPRES.code, Macro.CALORIES, 39.0)

        assertEquals(listOf(utile), Completions({ emptyMap() }).kept(listOf(CAPRES), listOf(utile)))
    }

    @Test
    fun `une completion perimee ne survit pas a la generation`() {
        // Le nettoyage doit traverser `generate` et pas seulement `kept` : c'est
        // `generate` qui rend ce que la tache ecrit dans le fichier.
        val perimee = CiqualCompletion(CAROTTE.code, Macro.CALORIES, 12.0)

        val produites = Completions({ mapOf((CAPRES.code to Macro.CALORIES) to 39.0) })
            .generate(listOf(CAROTTE, CAPRES), listOf(perimee))

        assertEquals(listOf(CAPRES.code), produites.map { it.code })
    }

    // --- Les lots --------------------------------------------------------------

    @Test
    fun `les trous partent par lots, et chaque lot annonce tout ce qui est acquis`() {
        val fiches = (1..5).map { troue("%05d".format(it)) }
        val etats = mutableListOf<Int>()
        val completions = Completions(
            { lot -> lot.associate { (it.code to it.macro) to 10.0 } },
            batchSize = 2,
        )

        completions.generate(fiches, emptyList()) { acquis -> etats += acquis.size }

        assertEquals(listOf(2, 4, 5), etats)
    }

    // --- La relecture de la reponse -------------------------------------------

    @Test
    fun `un preambule n emporte pas le lot`() {
        val reponse = "Voici les estimations :\n\n11040\tCALORIES\t39\n"

        assertEquals(mapOf(("11040" to Macro.CALORIES) to 39.0), parseCompletions(reponse))
    }

    @Test
    fun `une macro inconnue est ecartee sans emporter les autres`() {
        // **La ligne inconnue vient en second, et c'est ce qui fait le test.** Placee
        // en premier, un repli silencieux sur un compteur quelconque serait ecrase par
        // la ligne suivante et ne se verrait pas : le cas passait sans rien mesurer.
        val reponse = "11040\tFIBER\t3.6\n11040\tENERGIE\t39"

        assertEquals(mapOf(("11040" to Macro.FIBER) to 3.6), parseCompletions(reponse))
    }

    @Test
    fun `la virgule decimale est lue comme un point`() {
        // Un modele qui repond en francais ecrit « 3,6 », et perdre la ligne pour un
        // separateur serait une depense refaite pour rien.
        assertEquals(mapOf(("11040" to Macro.FIBER) to 3.6), parseCompletions("11040\tFIBER\t3,6"))
    }

    @Test
    fun `une valeur illisible est ecartee`() {
        assertTrue(parseCompletions("11040\tCALORIES\tenviron trente").isEmpty())
    }

    // --- Le fichier -----------------------------------------------------------

    @Test
    fun `un fichier absent rend une liste vide, sans echouer`() {
        assertTrue(CompletionsCsv.read(File(directory.toFile(), "absent.csv"), TABLE).isEmpty())
    }

    @Test
    fun `l aller-retour par le fichier ne perd rien`() {
        val completions = listOf(CiqualCompletion(CAPRES.code, Macro.CALORIES, 39.0))
        val file = File(directory.toFile(), "completions.csv")

        CompletionsCsv.write(file, completions)

        assertEquals(completions, CompletionsCsv.read(file, TABLE))
    }

    @Test
    fun `une completion sur une teneur publiee arrete l import`() {
        // **La regle qui commande tout.** Une ligne qui vise un trou inexistant est
        // soit fautive, soit un reliquat : dans les deux cas elle doit se voir, et non
        // dormir en attendant qu'un import ulterieur la fasse resurgir.
        val file = write("${CAROTTE.code},CALORIES,12")

        val faute = assertThrows<IllegalStateException> { CompletionsCsv.read(file, TABLE) }

        assertTrue(faute.message!!.contains(CAROTTE.code), "la ligne fautive est nommee")
    }

    @Test
    fun `un code inexistant arrete l import`() {
        assertThrows<IllegalStateException> { CompletionsCsv.read(write("99999,CALORIES,12"), TABLE) }
    }

    @Test
    fun `une macro inconnue arrete l import`() {
        // **La fiche n'a aucune teneur publiee**, et c'est ce qui isole la regle : sur
        // une fiche qui en publie, un repli silencieux sur un autre compteur serait
        // attrape par le controle du trou -- et le cas passerait sans rien mesurer.
        assertThrows<IllegalStateException> { CompletionsCsv.read(write("${SANS_RIEN.code},ENERGIE,39"), TABLE) }
    }

    @Test
    fun `une teneur negative arrete l import`() {
        assertThrows<IllegalStateException> { CompletionsCsv.read(write("${CAPRES.code},CALORIES,-3"), TABLE) }
    }

    @Test
    fun `deux teneurs pour un meme couple arretent l import`() {
        val file = write("${CAPRES.code},CALORIES,39", "${CAPRES.code},CALORIES,41")

        assertThrows<IllegalStateException> { CompletionsCsv.read(file, TABLE) }
    }

    @Test
    fun `deux macros pour une meme fiche ne sont pas un doublon`() {
        val file = write("${SANS_RIEN.code},CALORIES,39", "${SANS_RIEN.code},FIBER,3.6")

        assertEquals(2, CompletionsCsv.read(file, TABLE).size)
    }

    @Test
    fun `le fichier est ecrit trie par code puis par compteur`() {
        val file = File(directory.toFile(), "completions.csv")
        CompletionsCsv.write(
            file,
            listOf(
                CiqualCompletion(SANS_RIEN.code, Macro.FIBER, 3.6),
                CiqualCompletion(CAPRES.code, Macro.CALORIES, 39.0),
                CiqualCompletion(SANS_RIEN.code, Macro.CALORIES, 39.0),
            ),
        )

        assertEquals(
            listOf(SANS_RIEN.code to Macro.CALORIES, SANS_RIEN.code to Macro.FIBER, CAPRES.code to Macro.CALORIES),
            CompletionsCsv.read(file, TABLE).map { it.code to it.macro },
            "par code croissant, puis dans l ordre des six compteurs",
        )
    }

    private fun write(vararg rows: String): File {
        val file = File(directory.toFile(), "completions.csv")
        file.writeText("code_ciqual,macro,teneur\n" + rows.joinToString("\n") + "\n")
        return file
    }

    /**
     * Une fiche a **un seul** trou : son energie.
     *
     * Une fiche sans aucune teneur en porterait six, et le compte des lots ne dirait
     * plus rien du decoupage -- cinq fiches feraient trente demandes.
     */
    private fun troue(code: String) = ciqualFood(
        code = code,
        name = "Aliment $code",
        category = null,
        nutrients = Macro.entries.filterNot { it == Macro.CALORIES }.associate { it.nutrient to 1.0 },
    )

    private companion object {
        /** Sans énergie, le reste publié : le cas type d'une fiche à combler. */
        val CAPRES = ciqualFood(
            code = "11040",
            name = "Capres, au vinaigre",
            category = null,
            nutrients = mapOf(
                Nutrient.PROTEIN to 2.18,
                Nutrient.CARB to 3.5,
                Nutrient.SUGAR to 0.4,
                Nutrient.FAT to 0.86,
                Nutrient.FIBER to 3.6,
            ),
        )

        /** Les six teneurs publiées : rien à demander. */
        val CAROTTE = ciqualFood(
            code = "20009",
            name = "Carotte, crue",
            category = null,
            nutrients = Macro.entries.associate { it.nutrient to 1.0 },
        )

        /** Tout est publié sauf les fibres. */
        val HUILE = ciqualFood(
            code = "17270",
            name = "Huile d'olive vierge extra",
            category = null,
            nutrients = Macro.entries.filterNot { it == Macro.FIBER }.associate { it.nutrient to 1.0 },
        )

        /** Dense, et sans energie determinee : le cas ou le plafond des grammes ne vaut pas. */
        val HUILE_SANS_ENERGIE = ciqualFood(
            code = "17271",
            name = "Huile de tournesol",
            category = null,
            nutrients = mapOf(Nutrient.PROTEIN to 0.0, Nutrient.CARB to 0.0, Nutrient.FAT to 100.0),
        )

        /** Aucune teneur : six trous. */
        val SANS_RIEN = ciqualFood(
            code = "00001",
            name = "Aliment sans aucune teneur",
            category = null,
            nutrients = emptyMap(),
        )

        val TABLE = listOf(CAPRES, CAROTTE, HUILE, HUILE_SANS_ENERGIE, SANS_RIEN).associateBy { it.code }
    }
}
