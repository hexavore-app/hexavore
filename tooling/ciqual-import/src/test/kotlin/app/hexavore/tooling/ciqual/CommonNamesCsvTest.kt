package app.hexavore.tooling.ciqual

import app.hexavore.domain.language.ContentLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * La lecture de la table des noms courants ([D164][decisions]).
 *
 * Chaque refus nomme la ligne, et chacun a son cas : c'est un fichier écrit à la main,
 * et c'est à la main qu'il se trompe. Les libellés sont ceux de l'ANSES.
 *
 * [decisions]: docs/11-decisions.md
 */
class CommonNamesCsvTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `un nom est lu normalise, et rattache a son code`() {
        // Le fichier garde ses accents pour se relire ; l'index, lui, ne connait que la
        // forme normalisee, et c'est elle que la recherche compare.
        val lus = CommonNamesCsv.read(write("18004,Café noir", "9104,riz"), LABELS)

        assertEquals(listOf(CiqualCommonName("18004", "cafe noir"), CiqualCommonName("9104", "riz")), lus)
    }

    @Test
    fun `plusieurs noms pour une meme fiche sont permis`() {
        val lus = CommonNamesCsv.read(write("9104,riz", "9104,riz blanc"), LABELS)

        assertEquals(listOf("riz", "riz blanc"), lus.map { it.name })
    }

    @Test
    fun `un fichier absent rend une liste vide`() {
        // Une langue sans table se classe sur ses seuls libelles : l'etat d'avant.
        assertTrue(CommonNamesCsv.read(File(directory.toFile(), "absent.csv"), LABELS).isEmpty())
    }

    @Test
    fun `un code inexistant arrete l import en nommant la ligne`() {
        val faute = assertThrows<IllegalStateException> {
            CommonNamesCsv.read(write("9104,riz", "99999,inconnu"), LABELS)
        }

        assertTrue(faute.message!!.contains(":3 "), "la ligne fautive est nommee : ${faute.message}")
        assertTrue(faute.message!!.contains("99999"))
    }

    @Test
    fun `un nom vide une fois normalise arrete l import`() {
        // « ... » n'est pas vide a l'oeil, mais il l'est pour l'index : il ne
        // designerait rien, et rien ne le trouverait.
        val faute = assertThrows<IllegalStateException> { CommonNamesCsv.read(write("9104,..."), LABELS) }

        assertTrue(faute.message!!.contains(":2 "), "la ligne fautive est nommee : ${faute.message}")
    }

    @Test
    fun `un nom qui designe deux fiches arrete l import`() {
        // Les deux fiches seraient a egalite sur le mot meme qu'on a voulu trancher. Le
        // doublon est normalise : « café » et « cafe » sont le meme nom pour l'index.
        val faute = assertThrows<IllegalStateException> {
            CommonNamesCsv.read(write("18004,café", "18001,cafe"), LABELS)
        }

        assertTrue(faute.message!!.contains(":3 "), "la ligne du doublon est nommee : ${faute.message}")
        assertTrue(faute.message!!.contains("ligne 2"), "et celle qui portait deja le nom : ${faute.message}")
    }

    @Test
    fun `un meme nom ecrit deux fois pour une fiche arrete l import`() {
        // Inoffensif pour le score, mais c'est le signe d'une relecture qui a perdu le
        // fil : « oeuf » et « œuf » sont un seul nom.
        assertThrows<IllegalStateException> { CommonNamesCsv.read(write("22010,oeuf", "22010,œuf"), LABELS) }
    }

    @Test
    fun `un nom egal au libelle d une autre fiche arrete l import`() {
        // « Riz blanc, cru » est un libelle : le donner au riz cuit mettrait les deux
        // fiches a egalite de nom, et la brievete rendrait le cru.
        val faute = assertThrows<IllegalStateException> { CommonNamesCsv.read(write("9104,riz blanc cru"), LABELS) }

        assertTrue(faute.message!!.contains("9100"), "la fiche concurrente est nommee : ${faute.message}")
    }

    @Test
    fun `un nom egal au libelle de sa propre fiche arrete l import`() {
        // Il ne servirait a rien : le libelle est deja pese.
        val faute = assertThrows<IllegalStateException> {
            CommonNamesCsv.read(write("32140,Flocons d'avoine"), LABELS)
        }

        assertTrue(faute.message!!.contains(":2 "), "la ligne fautive est nommee : ${faute.message}")
    }

    @Test
    fun `une ligne sans separateur arrete l import`() {
        assertThrows<IllegalStateException> { CommonNamesCsv.read(write("9104"), LABELS) }
    }

    @Test
    fun `un en-tete inattendu arrete l import`() {
        val file = File(directory.toFile(), "common-names.csv")
        file.writeText("code_ciqual,titre_court\n9104,riz\n")

        assertThrows<IllegalStateException> { CommonNamesCsv.read(file, LABELS) }
    }

    /**
     * Les deux fichiers livrés passent leur propre lecteur, contre les libellés de leur
     * langue.
     *
     * Le lecteur n'est éprouvé ailleurs que sur des décors ; c'est ici que le fichier
     * relu à la main rencontre ses règles, sans attendre l'import pour le savoir.
     */
    @Test
    fun `les fichiers livres se lisent sans refus`() {
        val catalogue = File(System.getProperty("user.dir")).resolve("../ciqual")
        val table = CiqualArchive(catalogue.resolve(CatalogueFiles.ARCHIVE)).use { CiqualReader(it).read() }
        val files = CatalogueFiles(catalogue, File("inutilise"))

        ContentLanguage.entries.forEach { language ->
            val names = CommonNamesCsv.read(
                files.commonNames(language),
                table.foods.associate { it.code to it.label(language).name },
            )
            assertTrue(names.isNotEmpty(), "le fichier ${language.tag} est livre")
        }
    }

    private fun write(vararg rows: String): File {
        val file = File(directory.toFile(), "common-names.csv")
        file.writeText("code_ciqual,nom_courant\n" + rows.joinToString("\n") + "\n")
        return file
    }

    private companion object {
        /** Des libellés de l'ANSES, en français. */
        val LABELS = mapOf(
            "9100" to "Riz blanc, cru",
            "9104" to "Riz blanc, cuit, sans sel ajouté",
            "18001" to "Café, moulu",
            "18004" to "Café, non instantané, sans sucres ajoutés, prêt à boire",
            "22010" to "Oeuf dur",
            "32140" to "Flocons d'avoine",
        )
    }
}
