package app.hexavore.integration.reports

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.hexavore.core.testing.TestDispatchers
import app.hexavore.domain.report.REPORT_ADDRESS
import app.hexavore.domain.report.Report
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Le signalement s'adresse à un client de courriel, et à rien d'autre.
 *
 * ### Ce que ce test attrape
 *
 * `ACTION_SEND` porte une pièce jointe, et c'est pour ça qu'on l'emploie ; mais le type
 * `message/rfc822` est revendiqué par les messageries, le stockage en ligne et les
 * réseaux. L'appui ouvrait donc le choisisseur d'Android avec WhatsApp, Discord, Drive
 * et Telegram, dont aucun ne sait écrire à une adresse ([D154][decisions]).
 *
 * Le **sélecteur** est ce qui ramène la résolution aux seules applications de courriel.
 * Il est invisible à l'œil, il ne change rien à ce que l'écran montre, et le retirer par
 * mégarde ne casserait rien de ce que les autres tests observent : exactement le genre
 * de détail qui a besoin d'être tenu ici.
 *
 * ### Sous Robolectric
 *
 * Une `Intent` est une classe d'Android : sur la JVM nue, ses méthodes lèvent. Il n'y a
 * ici ni activité ni appareil, seulement la fabrique d'intention — ce qui est éprouvé
 * est ce qu'on envoie, pas ce qu'un téléphone en fait ([D35][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@RunWith(RobolectricTestRunner::class)
class MailIntentTest {
    @Test
    fun `avec une piece jointe, la resolution est bornee aux clients de courriel`() {
        val intent = reports().mailIntent(rapport(), listOf(FICHIER))

        val selecteur = intent.selector
        assertNotNull("sans selecteur, tout ce qui sait partager repond", selecteur)
        assertEquals(Intent.ACTION_SENDTO, selecteur?.action)
        assertEquals("mailto", selecteur?.data?.scheme)
    }

    @Test
    fun `la piece jointe voyage, et sa lecture est accordee`() {
        val intent = reports().mailIntent(rapport(), listOf(FICHIER))

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(FICHIER, intent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertTrue(
            "sans ce drapeau, le client de courriel ne peut pas lire le fichier",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
    }

    @Test
    fun `deux pieces jointes passent par l envoi multiple`() {
        val intent = reports().mailIntent(rapport(), listOf(FICHIER, AUTRE))

        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        assertEquals(2, intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.size)
        assertEquals("mailto", intent.selector?.data?.scheme)
    }

    /**
     * Sans pièce jointe, `mailto:` suffit et **porte l'adresse**.
     *
     * Pas de sélecteur alors : l'intention est déjà celle d'un courriel, et lui en
     * ajouter un reviendrait à la restreindre à elle-même.
     */
    @Test
    fun `sans piece jointe, l adresse est dans le mailto`() {
        val intent = reports().mailIntent(rapport(), emptyList())

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto:$REPORT_ADDRESS", intent.data.toString())
        assertEquals(null, intent.selector)
    }

    @Test
    fun `l adresse et le sujet partent dans tous les cas`() {
        val intent = reports().mailIntent(rapport(), listOf(FICHIER))

        assertEquals(REPORT_ADDRESS, intent.getStringArrayExtra(Intent.EXTRA_EMAIL)?.single())
        assertEquals("Sujet", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("Corps", intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    private fun reports() =
        MailReports(ApplicationProvider.getApplicationContext(), TestDispatchers(Dispatchers.Unconfined))

    private fun rapport() = Report(subject = "Sujet", body = "Corps")

    private companion object {
        val FICHIER: Uri = Uri.parse("content://app.hexavore.reports/reports/echange.txt")
        val AUTRE: Uri = Uri.parse("content://app.hexavore.reports/reports/assiette.jpg")
    }
}
