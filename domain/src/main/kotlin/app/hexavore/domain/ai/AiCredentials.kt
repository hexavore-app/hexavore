package app.hexavore.domain.ai

import kotlinx.coroutines.flow.Flow

/**
 * Ce que l'utilisateur a renseigné pour **un** fournisseur.
 *
 * Séparé d'[AiConfiguration], qui porte en plus *quel* fournisseur : là-bas c'est ce
 * qu'on envoie à un appel, ici c'est ce qu'on garde d'une ligne de l'écran. Les
 * fusionner ferait porter le fournisseur deux fois — dans l'objet et dans la clé de
 * la carte qui le range —, et deux porteurs d'un même fait finissent par ne plus être
 * d'accord.
 */
data class ProviderCredentials(val apiKey: ApiKey, val model: String, val baseUrl: String)

/**
 * Ce que l'écran des réglages affiche : ce qui est renseigné, et lequel sert.
 *
 * Aucune opération de [AiCredentials] ne laisse `active` désigner un fournisseur
 * absent de [credentials] — effacer le fournisseur actif n'en laisse aucun. **La
 * lecture reste malgré tout défensive** : l'état vient d'un fichier que l'application
 * ne contrôle pas seule, et une combinaison incohérente doit se lire comme « aucun
 * fournisseur utilisable » plutôt que faire tomber l'écran.
 */
data class AiSetup(val active: AiProvider? = null, val credentials: Map<AiProvider, ProviderCredentials> = emptyMap())

/**
 * La configuration utilisable, s'il y en a une.
 *
 * Ici plutôt que recopiée dans chaque implémentation : c'est la dérivation qui relie
 * [AiCredentials] à [AiSettings], et deux copies d'une même règle divergent le jour où
 * l'une devient défensive et l'autre non.
 */
fun AiSetup.activeConfiguration(): AiConfiguration? {
    val provider = active ?: return null
    return credentials[provider]?.let {
        AiConfiguration(
            provider = provider,
            apiKey = it.apiKey,
            model = it.model,
            baseUrl = it.baseUrl,
            // **Des que le fournisseur sait appeler des outils**, et sans le
            // demander. C'etait un reglage, et c'etait un mauvais reglage : eteinte,
            // l'analyse rend des lignes que le catalogue n'a pas rejointes et que
            // personne ne complete ; allumee, le modele choisit lui-meme la fiche.
            // Un interrupteur dont une position est toujours moins bonne n'est pas un
            // choix, c'est un piege (D142).
            deepAnalysis = provider.tooling,
        )
    }
}

/**
 * Lire et écrire ce que l'utilisateur a configuré.
 *
 * **Distinct d'[AiSettings], qui n'en lit qu'une facette.** Le résolveur n'a besoin
 * que de la configuration active et n'a aucune raison de pouvoir l'effacer ; c'est la
 * ségrégation d'interfaces de [docs/06][archi], appliquée comme pour le catalogue
 * d'aliments. Une seule classe implémente les deux.
 *
 * **Un flux et non une lecture unique** : l'écran affiche ce qu'il vient d'écrire, et
 * un instantané l'obligerait à relire après chaque geste — c'est le défaut que
 * [D53][decisions] a nommé sur la recherche.
 *
 * **La clé se relit.** [docs/05][ia] veut un champ masqué avec révélation temporaire,
 * pas un champ qu'on ne peut que réécrire : quelqu'un qui doute de sa clé doit pouvoir
 * la vérifier sans la recoller.
 *
 * [archi]: docs/06-architecture.md
 * [ia]: docs/05-ia.md
 * [decisions]: docs/11-decisions.md
 */
interface AiCredentials {
    fun observe(): Flow<AiSetup>

    /**
     * Enregistre — et **rend ce fournisseur actif**, parce que renseigner une clé sans
     * s'en servir n'est jamais ce qu'on voulait faire.
     */
    suspend fun save(provider: AiProvider, credentials: ProviderCredentials)

    /** Efface la clé de ce fournisseur. S'il était actif, plus rien ne l'est. */
    suspend fun forget(provider: AiProvider)

    /** Choisit lequel sert, parmi ceux qui sont renseignés. */
    suspend fun activate(provider: AiProvider)
}
