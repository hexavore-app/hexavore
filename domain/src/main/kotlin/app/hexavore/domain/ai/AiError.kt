package app.hexavore.domain.ai

/**
 * Ce qui peut empêcher une analyse d'aboutir.
 *
 * **Une hiérarchie fermée, et le code HTTP ne remonte jamais jusqu'à l'écran.** Un
 * `401` n'est pas un message : « Votre clé a été refusée. Vérifiez-la dans les
 * réglages » en est un. Chaque cas a un message et **la plupart ont une action** —
 * ouvrir les réglages, réessayer, basculer en saisie manuelle. Un message d'erreur
 * sans issue est un défaut d'ergonomie ([docs/05][ia] § Erreurs).
 *
 * [ia]: docs/05-ia.md
 */
sealed interface AiError {
    /** Aucune clé enregistrée : les modes IA sont visibles mais grisés. */
    data object NoProviderConfigured : AiError

    /** `401`, `403`. La clé est là mais le fournisseur la refuse. */
    data object InvalidKey : AiError

    /** `429`, ou crédits épuisés. La clé est bonne, l'appel ne passera pas maintenant. */
    data object QuotaExceeded : AiError

    /**
     * `503`, `529`. Le fournisseur est **débordé**, et cela n'a rien à voir avec nous.
     *
     * **Distinct de [QuotaExceeded]**, qui dit *vous avez trop demandé*, et de [Server],
     * qui dit *quelque chose s'est mal passé*. Celui-ci dit *revenez dans un instant* —
     * trois phrases, trois gestes, et les confondre envoyait quelqu'un vérifier une clé
     * irréprochable. Les modèles gratuits les plus demandés rendent ce code aux heures
     * de pointe plusieurs fois par jour ([D156][decisions]).
     *
     * **Rien n'est facturé**, ce qui est l'argument qui autorise à repartir tout seul :
     * la relance ne coûte que du temps, et [app.hexavore.domain.usecase.AnalyseMeal] en
     * tente une avant de remonter cette issue.
     *
     * [decisions]: docs/11-decisions.md
     */
    data object ProviderBusy : AiError

    data object NoNetwork : AiError

    data object Timeout : AiError

    /**
     * Le modèle configuré ne lit pas les images.
     *
     * Détecté à l'enregistrement de la clé et non au moment où l'utilisateur veut
     * s'en servir : découvrir devant une assiette que le mode photo est indisponible
     * est le pire moment pour l'apprendre.
     */
    data object VisionUnsupported : AiError

    /** La réponse n'est pas exploitable : ni tableau, ni objet qui en contienne un. */
    data object Unparseable : AiError

    /**
     * La réponse était lisible et ne contenait aucune ligne exploitable.
     *
     * **Distinct d'[Unparseable]**, et le neuvième cas que [docs/05][ia] n'avait pas
     * prévu. Les deux n'invitent pas au même geste : une réponse illisible se
     * réessaie telle quelle, une assiette que le modèle n'a pas su lire se rephotographie
     * ou se décrit. Les confondre annoncerait un défaut technique là où il n'y a
     * qu'une photo trop sombre.
     *
     * C'est aussi ce qui tient la promesse « jamais de liste vide silencieuse » :
     * sans ce cas, un tableau vide ouvrirait un écran de validation sans lignes.
     *
     * [ia]: docs/05-ia.md
     */
    data object NothingRecognized : AiError

    /**
     * Tout le reste, avec son statut et **ce que le fournisseur en a dit**.
     *
     * [detail] est le message d'erreur brut, tel qu'il arrive. Il n'a pas sa place
     * dans un écran d'analyse — quelqu'un qui note un repas ne peut rien en faire, et
     * [docs/05][ia] a raison d'écrire qu'un code HTTP ne remonte jamais là.
     *
     * **Il en a une sur l'écran des réglages**, qui est un instrument de diagnostic :
     * son bouton « Tester » n'existe que pour dire ce qui ne va pas avant qu'on
     * compte dessus. « Le service est indisponible » y est un message qui ne dit rien
     * — ni à l'utilisateur, ni à qui doit corriger. La phrase du fournisseur, elle,
     * nomme le champ refusé ou le compte sans crédits.
     *
     * `null` quand le corps d'erreur est absent ou illisible.
     *
     * [ia]: docs/05-ia.md
     */
    data class Server(val status: Int, val detail: String? = null) : AiError
}

/**
 * Combien de temps on laisse passer l'embouteillage avant de repartir.
 *
 * **Trois secondes, et le chiffre n'est pas calibré** — aucun fournisseur ne publie la
 * durée de ses pics. Il est choisi contre deux échecs qui se voient : trop court, la
 * relance tombe dans le même embouteillage et ne sert qu'à doubler la facture de temps ;
 * trop long, l'attente devient indistinguable d'une application figée.
 *
 * Une constante nommée plutôt qu'un littéral, pour que le jour du calibrage n'ait qu'un
 * mot à changer — et pour que le test qui avance l'horloge virtuelle dise *la durée de
 * relance* plutôt que *trois mille*.
 */
const val BUSY_RETRY_DELAY_MILLIS: Long = 3_000
