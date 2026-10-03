package app.hexavore.domain.ai

/**
 * Un fournisseur de reconnaissance, et ce qu'il faut en savoir pour le proposer.
 *
 * **L'énumération ne porte que les fournisseurs implémentés**, et elle grandit avec
 * eux. Une entrée sans classe laisserait une branche morte dans la fabrique, qui
 * devrait alors rendre une erreur pour un fournisseur que l'écran des réglages vient
 * d'offrir — un choix qu'on présente et qu'on refuse. C'est aussi ce qui fait de
 * « ajouter un fournisseur » une opération vérifiable : le `when` de la fabrique est
 * exhaustif, donc une entrée nouvelle **ne compile pas** tant que sa classe n'existe
 * pas ([docs/05][ia] § Ajouter un fournisseur).
 *
 * **[displayName] est ici et non dans les ressources** : ce sont des noms de marque,
 * ils ne se traduisent pas. Les mettre ailleurs obligerait l'écran à un `when` sur le
 * fournisseur — précisément le signal que [docs/12][plan] nomme comme la fuite de
 * l'abstraction.
 *
 * [ia]: docs/05-ia.md
 * [plan]: docs/12-plan-de-developpement.md
 */
enum class AiProvider(
    val displayName: String,
    /** Modifiable par l'utilisateur : c'est ce qui rend un relais ou un modèle local branchable. */
    val defaultBaseUrl: String,
    /**
     * Ce qu'on propose de saisir, quand on le sait de source sûre.
     *
     * Vide plutôt que deviné : un identifiant de modèle écrit de mémoire rend un
     * `404` que l'utilisateur lira comme une panne de l'application. Le champ reste
     * libre — la liste ne fait qu'épargner une frappe.
     */
    val suggestedModels: List<String>,
    val vision: VisionSupport,
    /**
     * Où l'on obtient une clé chez ce fournisseur. Vide quand il n'y a pas de console
     * unique — c'est le cas du relais générique, qui désigne un service inconnu.
     *
     * **Ici et non dans les ressources**, pour la même raison que [displayName] : une
     * URL de console n'est pas du texte à traduire, et la mettre ailleurs obligerait
     * l'écran à un `when` sur le fournisseur.
     */
    val consoleUrl: String = "",
    /**
     * Celui que l'application met en avant.
     *
     * **Un seul**, sans quoi le mot ne veut plus rien dire. Ce n'est pas un jugement
     * sur la qualité des modèles : c'est celui dont la mise en route demande le moins
     * de démarches à quelqu'un qui n'a jamais pris de clé d'API.
     */
    val recommended: Boolean = false,
    /**
     * Ce fournisseur sait-il appeler des outils ?
     *
     * **Une capacité et non une supposition.** L'analyse approfondie repose sur un
     * aller-retour où le modèle interroge le catalogue ; un fournisseur qui ne sait pas
     * le faire rendrait du texte là où on attend un appel, et l'analyse échouerait sans
     * que l'utilisateur comprenne pourquoi. La case se grise plutôt, et le dit.
     *
     * Vrai pour les deux fournisseurs éprouvés, dont la documentation le décrit et dont
     * la forme d'appel a été relevée — pas écrite de mémoire. Faux pour les autres,
     * comme leur statut : ce qui n'a pas été vérifié ne se promet pas.
     */
    val tooling: Boolean = false,
    /**
     * Si ce fournisseur est proposé aujourd'hui, ou tenu en réserve.
     *
     * **Le code des six existe et passe ses tests.** Ce qui manque aux quatre derniers
     * est ce qu'aucun test ne donne : un appel réel, avec une vraie clé, sur un vrai
     * compte. Les proposer sans cela reviendrait à faire payer à quelqu'un la
     * découverte d'un défaut que personne n'a cherché.
     *
     * Un drapeau plutôt qu'une suppression : rien n'est perdu, le `when` de la fabrique
     * reste exhaustif, et le jour où l'un d'eux est éprouvé, il revient en changeant un
     * mot.
     */
    val status: ProviderStatus = ProviderStatus.READY,
    /**
     * Ce fournisseur a-t-il un palier **gratuit** qui suffise à l'usage ?
     *
     * **La seule chose qui décide vraiment**, et elle n'était écrite nulle part
     * ([D138][decisions]). Quelqu'un qui découvre l'application ne compare pas des
     * fournisseurs : il se demande si la fonctionnalité va lui coûter de l'argent. Le
     * dire fait la différence entre une clé configurée et un écran qu'on referme.
     *
     * C'est une propriété du fournisseur et non un libellé d'écran : le jour où Google
     * ferme son palier gratuit, un seul mot change ici, et l'écran cesse de le promettre.
     *
     * [decisions]: docs/11-decisions.md
     */
    val free: Boolean = false,
    /**
     * L'adresse du service se choisit-elle ?
     *
     * **Non, pour tout le monde sauf le fournisseur libre.** Gemini et Claude ont une
     * adresse et une seule ; la montrer dans un formulaire revient à demander à
     * quelqu'un de vérifier une chaîne qu'il ne peut ni connaître ni corriger, et à
     * lui offrir de la casser ([D143][decisions]).
     *
     * Le fournisseur compatible est l'exception, et c'est toute sa raison d'exister :
     * son adresse **est** le réglage.
     *
     * [decisions]: docs/11-decisions.md
     */
    val editableBaseUrl: Boolean = false,
) {
    ANTHROPIC(
        displayName = "Anthropic",
        defaultBaseUrl = "https://api.anthropic.com/",
        suggestedModels = listOf("claude-opus-5", "claude-sonnet-5", "claude-haiku-4-5"),
        vision = VisionSupport.ALWAYS,
        consoleUrl = "https://platform.claude.com/settings/keys",
        tooling = true,
    ),

    GEMINI(
        displayName = "Google Gemini",
        // Le palier gratuit d'AI Studio : c'est ce qui en fait le fournisseur qu'on
        // met en tete, et le seul qu'on recommande a qui decouvre (D138).
        free = true,
        defaultBaseUrl = "https://generativelanguage.googleapis.com/",
        // Releves sur la documentation vivante, pas ecrits de memoire : les
        // identifiants que j'aurais devines n'existaient pas.
        suggestedModels = listOf("gemini-3.5-flash-lite", "gemini-3.7-flash", "gemini-2.5-flash"),
        vision = VisionSupport.ALWAYS,
        consoleUrl = "https://aistudio.google.com/api-keys",
        recommended = true,
        tooling = true,
    ),

    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/",
        // Releves sur la documentation vivante. Aucun de ces trois identifiants
        // n'est celui que j'aurais ecrit de memoire, et c'est la troisieme fois.
        suggestedModels = listOf("gpt-5.6-luna", "gpt-5.6-terra", "gpt-5.6-sol"),
        vision = VisionSupport.ALWAYS,
        status = ProviderStatus.SUSPENDED,
    ),

    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/",
        suggestedModels = listOf("deepseek-v4-flash", "deepseek-v4-pro"),
        status = ProviderStatus.SUSPENDED,
        // Sa documentation ne promet pas la lecture d'images. « Selon le modele »
        // dit exactement ce qu'on sait : le mode photo se signalera indisponible
        // tant qu'un sondage n'aura pas prouve le contraire.
        vision = VisionSupport.MODEL_DEPENDENT,
    ),

    MISTRAL(
        displayName = "Mistral",
        defaultBaseUrl = "https://api.mistral.ai/",
        suggestedModels = listOf("mistral-small-2603", "mistral-medium-3505", "mistral-large-2512"),
        vision = VisionSupport.MODEL_DEPENDENT,
        status = ProviderStatus.SUSPENDED,
    ),

    /**
     * N'importe quel service qui parle comme OpenAI.
     *
     * **Ce n'est pas un bouche-trou, c'est l'assurance-vie du projet** : une URL de
     * base et un nom de modele suffisent a brancher OpenRouter, Groq, un Ollama du
     * reseau local, LM Studio, ou un fournisseur qui n'existe pas encore. C'est le
     * seul dont l'URL par defaut est vide -- il n'y en a pas, et en proposer une
     * ferait croire a un service par defaut qui n'existe pas.
     */
    COMPATIBLE(
        displayName = "Compatible OpenAI",
        defaultBaseUrl = "",
        suggestedModels = emptyList(),
        vision = VisionSupport.MODEL_DEPENDENT,
        status = ProviderStatus.SUSPENDED,
        editableBaseUrl = true,
    ),
}

/**
 * Proposé, ou tenu en réserve.
 *
 * Deux fournisseurs ont été éprouvés sur un vrai compte ; les quatre autres attendent
 * de l'être. **Ce n'est pas une question de code** — il est écrit, testé, et sa
 * campagne de défaite est passée — mais de vérification : un appel réel dit des choses
 * qu'aucun serveur local ne dit, et c'est l'utilisateur qui paierait la découverte.
 */
enum class ProviderStatus {
    READY,
    SUSPENDED,
}

/**
 * Si le fournisseur lit les images, et si la réponse dépend du modèle choisi.
 *
 * Deux cas parce que [docs/05][ia] en distingue deux : certains fournisseurs
 * répondent « oui » pour toute leur gamme, d'autres « selon modèle ». Pour les
 * seconds, c'est le bouton **Tester** qui tranche à l'enregistrement de la clé — et
 * non l'instant où quelqu'un tient son téléphone au-dessus d'une assiette.
 *
 * [ia]: docs/05-ia.md
 */
enum class VisionSupport {
    /** Toute la gamme lit les images. */
    ALWAYS,

    /** Cela dépend du modèle : à éprouver avant tout usage réel. */
    MODEL_DEPENDENT,
}
