package app.hexavore.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.hexavore.core.testing.TestDispatchers
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.ApiKey
import app.hexavore.domain.ai.ProviderCredentials
import app.hexavore.domain.food.OffAccount
import app.hexavore.domain.notice.Notice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * « Effacer toutes mes données » tient-il sa promesse ?
 *
 * **C'est ici que la question se juge, et pas dans le cas d'usage.** Celui-ci compose
 * deux appels et se vérifie avec deux faux ; ce qui pouvait mentir est plus bas — la
 * liste de ce qu'on vide. L'effacement laissait jusqu'ici trois choses derrière lui :
 * l'état de l'adaptation hebdomadaire, le consentement photo, et le compteur d'appels.
 * Aucun test ne le voyait, parce qu'aucun test ne regardait les fichiers.
 *
 * Les cas portent donc sur de vraies `SharedPreferences`, sous Robolectric, et ils
 * affirment deux choses distinctes : **plus rien sur le disque**, et **plus rien dans
 * les flux** — un magasin qui garderait son `MutableStateFlow` d'avant annoncerait une
 * clé qui n'existe plus, jusqu'au prochain lancement.
 *
 * @see docs/09-donnees-et-sauvegarde.md
 */
@RunWith(RobolectricTestRunner::class)
class ErasablePreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dispatchers = TestDispatchers(UnconfinedTestDispatcher())

    private val aiFile = context.getSharedPreferences("erase-ai", Context.MODE_PRIVATE)
    private val adjustmentFile = context.getSharedPreferences("erase-adjustment", Context.MODE_PRIVATE)
    private val contributionFile = context.getSharedPreferences("erase-contribution", Context.MODE_PRIVATE)

    // Le chiffrement est la couture : ce qui se juge ici est ce qui reste dans les
    // fichiers, pas la solidite d'AES. Le meme faux qu'en StoredAiCredentialsTest.
    private val cipher = object : SecretCipher {
        override fun encrypt(plain: String): String = plain.reversed()

        override fun decrypt(encoded: String): String? = encoded.reversed()
    }
    private val deep = StoredDeepAnalysisSettings(aiFile, dispatchers)
    private val credentials = StoredAiCredentials(aiFile, cipher, dispatchers)
    private val contribution = StoredContributionSettings(contributionFile, cipher, dispatchers)
    private val adjustment = StoredAdjustmentSettings(adjustmentFile, dispatchers)
    private val noticeFile = context.getSharedPreferences("erase-notices", Context.MODE_PRIVATE)
    private val notices = StoredNoticeSettings(noticeFile, dispatchers)
    private val debug = StoredDebugSettings(aiFile, dispatchers)
    private val consent = StoredPhotoConsent(aiFile, dispatchers)

    // Les rappels partagent le fichier des pastilles : la meme question posee a
    // l'utilisateur, donc le meme endroit a vider.
    private val reminders = StoredReminderSettings(noticeFile, dispatchers)

    private val erasable = ErasablePreferences(
        stores = FlowBackedStores(credentials, contribution, adjustment, notices, debug, deep, reminders),
        files = listOf(aiFile, adjustmentFile, contributionFile, noticeFile),
        dispatchers = dispatchers,
    )

    @Test
    fun `la cle d API ne revient pas`() = runTest {
        credentials.save(AiProvider.ANTHROPIC, CLE)

        erasable.erase()

        assertNull(credentials.observe().first().credentials[AiProvider.ANTHROPIC])
    }

    @Test
    fun `le compte Open Food Facts ne revient pas`() = runTest {
        contribution.save(OffAccount("charly", "secret"))

        erasable.erase()

        assertNull(contribution.observe().first().account)
    }

    @Test
    fun `l etat de l adaptation ne revient pas`() = runTest {
        // **Un des trois oublis.** « Ne plus proposer » survivait a l'effacement, et la
        // carte d'ajustement restait muette sur une installation qu'on croyait neuve.
        adjustment.stop()
        adjustment.accepted(LUNDI)

        erasable.erase()

        val setup = adjustment.observe().first()
        assertTrue("une installation neuve a l'adaptation active", setup.enabled)
        assertNull(setup.lastAcceptedOn)
    }

    @Test
    fun `le consentement photo ne revient pas`() = runTest {
        // Le deuxieme oubli, et personne ne le modelisait : quelqu'un qui repart de
        // zero n'a rien accepte.
        consent.accept()

        erasable.erase()

        assertEquals(false, consent.accepted())
    }

    @Test
    fun `les reglages de pastilles reviennent a leur etat neuf`() = runTest {
        // Le quatrieme oubli possible : une pastille eteinte survivrait a un
        // effacement, et l'installation « neuve » serait muette sur ce qu'elle ne
        // sait pas encore faire.
        notices.setEnabled(Notice.WEIGHT_STALE, enabled = false)

        erasable.erase()

        assertTrue("toutes les pastilles se rallument", Notice.WEIGHT_STALE in notices.observe().first())
    }

    @Test
    fun `le mode debug se rallume eteint`() = runTest {
        // Le journal des echanges retient ce qu'on a envoye a un fournisseur :
        // quelqu'un qui efface ses cles n'a rien demande a voir de ce qui reste.
        debug.setEnabled(enabled = true)

        erasable.erase()

        assertEquals(false, debug.enabled())
    }

    @Test
    fun `l analyse approfondie repart decochee`() = runTest {
        // Une facon d'analyser, rangee avec les cles : quelqu'un qui repart de zero n'a
        // pas demande que le modele interroge son catalogue.
        deep.setEnabled(enabled = true)

        erasable.erase()

        assertEquals(false, deep.enabled())
    }

    @Test
    fun `aucun fichier ne garde quoi que ce soit`() = runTest {
        // **Le cas qui couvre ce que personne n'a modelise** — le compteur d'appels
        // d'aujourd'hui, et le reglage qu'on ajoutera l'an prochain sans penser a cette
        // classe. Les cles sont ecrites a la main, parce que c'est exactement ce que
        // fait un magasin qu'on n'a pas encore ecrit.
        aiFile.edit().putString("un_reglage_futur", "valeur").commit()
        adjustmentFile.edit().putInt("un_compteur", 12).commit()
        contributionFile.edit().putBoolean("un_drapeau", true).commit()
        noticeFile.edit().putString("un_reglage", "x").commit()

        erasable.erase()

        listOf(aiFile, adjustmentFile, contributionFile, noticeFile).forEach { fichier ->
            assertTrue("il reste ${fichier.all}", fichier.all.isEmpty())
        }
    }

    @Test
    fun `effacer deux fois ne casse rien`() = runTest {
        // Un double appui sur un bouton desactive trop tard : le second geste ne doit
        // pas lever sur un fichier deja vide.
        credentials.save(AiProvider.ANTHROPIC, CLE)

        erasable.erase()
        erasable.erase()

        assertNull(credentials.observe().first().credentials[AiProvider.ANTHROPIC])
    }

    private companion object {
        val CLE = ProviderCredentials(
            apiKey = ApiKey("sk-ant-de-test"),
            model = "claude-opus-5",
            baseUrl = "https://exemple.test/",
        )
        val LUNDI: LocalDate = LocalDate.of(2026, 8, 17)
    }
}
