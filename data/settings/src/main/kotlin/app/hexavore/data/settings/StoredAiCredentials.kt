package app.hexavore.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiCredentials
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.AiSettings
import app.hexavore.domain.ai.AiSetup
import app.hexavore.domain.ai.ApiKey
import app.hexavore.domain.ai.ProviderCredentials
import app.hexavore.domain.ai.activeConfiguration
import app.hexavore.domain.concurrency.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * Les réglages d'IA, rangés sur l'appareil — la clé chiffrée, le reste en clair.
 *
 * **Seule la clé passe par le chiffrement.** Le nom du modèle et l'URL de base ne sont
 * pas des secrets : les chiffrer coûterait deux déchiffrements par lecture pour
 * protéger « claude-opus-5 ». Ce qui engage l'argent de quelqu'un, et rien d'autre,
 * mérite le trousseau.
 *
 * **Un état en mémoire alimente le flux**, plutôt qu'une écoute des préférences. Ce
 * fichier n'a qu'un seul écrivain — cet objet —, et brancher un écouteur ferait
 * dépendre l'affichage d'un rappel qu'Android ne garantit pas de délivrer si personne
 * ne garde une référence forte dessus. C'est un piège classique de
 * `SharedPreferences`, payé par des écrans qui cessent de se rafraîchir au bout de
 * quelques minutes.
 *
 * **Une seule classe pour deux ports**, comme le catalogue d'aliments : la séparation
 * sert les appelants — le résolveur ne voit que [AiSettings] — pas le nombre d'objets.
 *
 * @see docs/05-ia.md § Sécurité des clés
 */
internal class StoredAiCredentials(
    private val preferences: SharedPreferences,
    private val cipher: SecretCipher,
    private val dispatchers: DispatcherProvider,
) : AiCredentials,
    AiSettings {
    private val setup = MutableStateFlow(preferences.readSetup(cipher))

    override fun observe(): Flow<AiSetup> = setup

    override suspend fun save(provider: AiProvider, credentials: ProviderCredentials) = write {
        preferences.edit {
            putString(provider.keyOf(SUFFIX_KEY), cipher.encrypt(credentials.apiKey.value))
            putString(provider.keyOf(SUFFIX_MODEL), credentials.model)
            putString(provider.keyOf(SUFFIX_URL), credentials.baseUrl)
            // Renseigner une cle sans s'en servir n'est jamais ce qu'on voulait faire.
            putString(ACTIVE, provider.name)
        }
    }

    override suspend fun forget(provider: AiProvider) = write {
        preferences.edit {
            remove(provider.keyOf(SUFFIX_KEY))
            remove(provider.keyOf(SUFFIX_MODEL))
            remove(provider.keyOf(SUFFIX_URL))
            // Effacer le fournisseur actif n'en laisse aucun : le designer encore
            // ferait promettre a l'ecran un appel qui echouerait.
            if (preferences.getString(ACTIVE, null) == provider.name) remove(ACTIVE)
        }
    }

    override suspend fun activate(provider: AiProvider) = write {
        // Activer ce qui n'est pas renseigne n'a pas de sens : on ne peut pas se
        // servir d'une cle qui n'est pas la.
        if (provider in setup.value.credentials) preferences.edit { putString(ACTIVE, provider.name) }
    }

    override suspend fun current(): AiConfiguration? = setup.value.activeConfiguration()

    /**
     * Écrit hors du fil principal, puis **relit** pour rafraîchir le flux.
     *
     * Relire plutôt que recalculer l'état en mémoire : c'est ce qui fait que
     * l'affichage montre ce que le disque contient, et non ce qu'on croit y avoir mis.
     * Un chiffrement qui échouerait silencieusement se verrait ici.
     */
    private suspend fun write(block: () -> Unit) = withContext(dispatchers.io) {
        block()
        setup.value = preferences.readSetup(cipher)
    }
}

/**
 * L'état complet, relu depuis les préférences.
 *
 * Hors de la classe, comme les lectures de la base de l'ANSES le sont du catalogue :
 * c'est une conversion de format, pas une capacité que le stockage expose.
 *
 * **Un fournisseur dont la clé ne se déchiffre pas est absent**, et non présent avec
 * une clé vide. Le trousseau peut avoir perdu sa clé — restauration sur un autre
 * appareil, verrou d'écran retiré — et la seule lecture honnête de ce chiffré est
 * « il n'y a rien ici ». Une chaîne vide ferait partir un appel voué au `401`.
 */
private fun SharedPreferences.readSetup(cipher: SecretCipher): AiSetup {
    val credentials = AiProvider.entries.mapNotNull { provider ->
        val stored = getString(provider.keyOf(SUFFIX_KEY), null) ?: return@mapNotNull null
        val apiKey = cipher.decrypt(stored) ?: return@mapNotNull null
        provider to ProviderCredentials(
            apiKey = ApiKey(apiKey),
            model = getString(provider.keyOf(SUFFIX_MODEL), null).orEmpty(),
            baseUrl = getString(provider.keyOf(SUFFIX_URL), null) ?: provider.defaultBaseUrl,
        )
    }.toMap()

    val active = getString(ACTIVE, null)?.let { name -> AiProvider.entries.firstOrNull { it.name == name } }
    return AiSetup(active = active.takeIf { it in credentials }, credentials = credentials)
}

/**
 * La clé de préférence d'un fournisseur.
 *
 * Préfixée par le nom de l'énumération : un fournisseur ajouté range ses valeurs sans
 * qu'aucune constante ne bouge, et un fournisseur retiré laisse des entrées que la
 * lecture ignore d'elle-même.
 */
private fun AiProvider.keyOf(suffix: String) = "$name.$suffix"

private const val ACTIVE = "active"
private const val SUFFIX_KEY = "key"
private const val SUFFIX_MODEL = "model"
private const val SUFFIX_URL = "url"
