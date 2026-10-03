package app.hexavore.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.hexavore.core.testing.TestDispatchers
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.ApiKey
import app.hexavore.domain.ai.ProviderCredentials
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Le contrat, joué sur le magasin qui écrit vraiment sur le disque.
 *
 * Les préférences sont réelles ; le chiffrement, non. `SecretCipher` est la couture
 * qui rend ce choix explicite : ce que le contrat éprouve est le rangement, et le
 * rangement ne dépend pas de l'algorithme. Le `KeystoreCipher` demande un trousseau
 * matériel, donc un appareil — voir [AiCredentialsContract].
 */
@RunWith(RobolectricTestRunner::class)
class StoredAiCredentialsTest : AiCredentialsContract() {
    override fun store(): AiCredentialsView = view(ReversingCipher())

    @Test
    fun `une cle dont le chiffre ne s ouvre plus se lit comme absente`() = runBlocking {
        // Le trousseau perd sa cle pour des raisons banales : sauvegarde restauree
        // sur un autre appareil, verrou d'ecran retire. Le chiffre survit alors a la
        // cle qui l'ouvrait. La lecture honnete est « rien ici » -- pas une chaine
        // vide, qui ferait partir un appel voue au 401.
        val illisible = ReversingCipher(readable = false)
        val magasin = view(ReversingCipher())
        magasin.save(AiProvider.ANTHROPIC, CLE)

        val relu = view(illisible)

        assertNull("un chiffre illisible ne doit pas passer pour une cle", relu.current())
        assertEquals(emptyMap<AiProvider, ProviderCredentials>(), relu.observe().first().credentials)
        // Et l'etat affiche doit etre coherent, pas seulement la configuration
        // derivee : sans cette ligne, l'ecran annoncerait « Utilise » a cote d'un
        // fournisseur dont la cle ne s'ouvre plus.
        assertNull("aucun fournisseur ne peut etre actif sans cle", relu.observe().first().active)
    }

    @Test
    fun `la cle n est pas ecrite en clair dans les preferences`() = runBlocking {
        val magasin = view(ReversingCipher())

        magasin.save(AiProvider.ANTHROPIC, CLE)

        val tout = preferences.all.values.joinToString(separator = " ")
        assertEquals(false, tout.contains(CLE.apiKey.value))
    }

    @Test
    fun `le modele et l URL ne sont pas chiffres`() = runBlocking {
        // Chiffrer « claude-opus-5 » couterait deux dechiffrements par lecture pour
        // proteger ce qui n'est pas un secret. Ce cas fige le partage.
        val magasin = view(ReversingCipher())

        magasin.save(AiProvider.ANTHROPIC, CLE)

        val tout = preferences.all.values.joinToString(separator = " ")
        assertEquals(true, tout.contains(CLE.model))
    }

    private val preferences by lazy {
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("ai_settings_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }
    }

    private fun view(cipher: SecretCipher): AiCredentialsView {
        val stored = StoredAiCredentials(
            preferences,
            cipher,
            TestDispatchers(UnconfinedTestDispatcher()),
        )
        return object : AiCredentialsView, app.hexavore.domain.ai.AiCredentials by stored {
            override suspend fun current() = stored.current()
        }
    }

    private companion object {
        val CLE = ProviderCredentials(
            apiKey = ApiKey("sk-ant-de-test"),
            model = "claude-opus-5",
            baseUrl = "https://api.anthropic.com/",
        )
    }
}

/**
 * Un chiffrement de pacotille, qui ne prétend à rien.
 *
 * Il inverse la chaîne : assez pour que la valeur écrite ne soit pas la valeur
 * saisie — ce que deux cas vérifient — et assez pour qu'un aller-retour se referme.
 * `readable = false` reproduit un trousseau qui a perdu sa clé.
 */
private class ReversingCipher(private val readable: Boolean = true) : SecretCipher {
    override fun encrypt(plain: String): String = plain.reversed()

    override fun decrypt(encoded: String): String? = encoded.reversed().takeIf { readable }
}
