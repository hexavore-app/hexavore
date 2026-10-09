package app.hexavore.core.database.ciqual

import androidx.test.core.app.ApplicationProvider
import app.hexavore.core.database.ROBOLECTRIC_SDK
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * La copie du catalogue, et l'état où elle laissait la recherche morte.
 *
 * Un fichier de zéro octet s'ouvre sans broncher : SQLite y lit une base vide. Tant que
 * la condition de recopie ne regardait que l'existence du fichier, cet état ne se
 * réparait **jamais** — chaque recherche répondait « catalogue illisible », et
 * réinstaller était le seul recours ([D139][decisions]).
 *
 * On l'a vu sous Android 11, où un plantage a emporté le processus pendant la première
 * copie. La cause du plantage est corrigée ailleurs ; ce test tient l'autre moitié, qui
 * est que l'application sache se remettre d'une copie ratée, quelle qu'en soit la
 * raison.
 *
 * [decisions]: docs/11-decisions.md
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class CiqualCopyTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `une copie vide se refait au lieu de condamner la recherche`() {
        val copie = File(context.filesDir, FICHIER)
        copie.parentFile?.mkdirs()
        copie.writeBytes(ByteArray(0))

        val trouve = CiqualDatabase(context).search(
            normalisedQuery = "",
            categories = setOf(RAYON),
            limit = LIMITE,
            language = LANGUE,
        )

        assertTrue("Le fichier vide n'a pas ete remplace.", copie.length() > 0)
        assertTrue("Le catalogue recopie ne rend rien.", trouve.isNotEmpty())
    }

    @Test
    fun `une premiere ouverture copie le catalogue`() {
        File(context.filesDir, FICHIER).delete()

        val trouve = CiqualDatabase(context).search(
            normalisedQuery = "",
            categories = setOf(RAYON),
            limit = LIMITE,
            language = LANGUE,
        )

        assertTrue("Rien n'a ete copie depuis les assets.", trouve.isNotEmpty())
    }

    private companion object {
        /**
         * Le nom que porte la copie, recopie ici a dessein.
         *
         * Le lire depuis la classe testee ferait passer les deux tests meme si le nom
         * changeait de forme : ce fichier dit **ou** le test va poser son fichier vide,
         * et c'est une affirmation, pas un emprunt.
         */
        const val FICHIER = "ciqual-2025-11-03-r7.db"

        /** Un rayon qui existe dans toutes les editions, pour feuilleter sans chercher. */
        const val RAYON = "FRUITS"
        const val LANGUE = "fr"
        const val LIMITE = 5
    }
}
