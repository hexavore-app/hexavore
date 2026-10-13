package app.hexavore.domain.ai

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Un aller-retour avec un fournisseur, tel qu'on peut le relire.
 *
 * **C'est un instrument de mise au point, et il est éteint par défaut.** Ce qu'il
 * montre — le corps envoyé, le corps reçu — est exactement ce dont on a besoin pour
 * comprendre pourquoi un modèle a répondu ce qu'il a répondu, et exactement ce qu'on
 * ne veut pas garder sans l'avoir demandé : la description d'un repas, la photo d'une
 * assiette, les libellés qu'on a cherchés.
 *
 * **Rien n'est écrit sur le disque.** Le journal vit en mémoire et meurt avec le
 * processus ([AiExchangeLog]). Ce n'est pas une limite qu'on lèvera plus tard : un
 * fichier de mise au point qui survit à la session est un fichier que personne
 * n'efface, et qui finit dans une sauvegarde.
 *
 * @see docs/05-ia.md § Sécurité des clés
 */
data class AiExchange(
    val at: Instant,
    /** Le point d'entrée appelé, **sans la chaîne de requête** — certains y mettent la clé. */
    val endpoint: String,
    val status: Int,
    val request: String,
    val response: String,
)

/**
 * Les derniers échanges, du plus récent au plus ancien.
 *
 * **Une file bornée.** Une analyse de photo pèse quelques centaines de kilooctets une
 * fois les images élidées ; en garder l'historique complet ferait grossir la mémoire
 * de l'application aussi longtemps qu'elle tourne, pour un usage qui ne regarde jamais
 * plus loin que les deux ou trois derniers.
 */
interface AiExchangeLog {
    fun observe(): Flow<List<AiExchange>>

    /**
     * Note un échange. **Ne suspend pas.**
     *
     * L'appelant est un intercepteur HTTP, sur le fil d'OkHttp : lui demander une
     * coroutine obligerait à en lancer une par requête, et à décider ce qui arrive
     * quand elle est annulée pendant que l'appel, lui, continue.
     */
    fun record(exchange: AiExchange)

    fun clear()
}

/**
 * Ce qu'on garde, et **tout ce qu'on garde**.
 *
 * Un seul échange : celui qu'un signalement joint. Le bouton de signalement promet
 * d'envoyer « ce qui a été demandé à l'IA et ce qu'elle a répondu » ; sans cette
 * mémoire-là il n'envoyait que le corps du courriel, et la promesse était fausse
 * ([D153][decisions]).
 *
 * **C'est devenu la seule profondeur.** Il y en avait deux : vingt échanges quand le
 * mode de mise au point était allumé, un sinon. L'interrupteur est parti avec l'écran
 * qu'il ouvrait — signaler un plat joint déjà l'échange, donc un écran pour le relire
 * à la main ne servait plus personne ([D156][decisions]). Ce qui reste est la règle qui
 * valait déjà par défaut : rien ne s'**accumule**, et on cesse seulement de ne rien
 * retenir du tout, parce qu'une fonction de l'application en dépend.
 *
 * Il vit en mémoire, meurt avec le processus, ne part que si l'utilisateur appuie sur
 * « signaler » **puis** sur « envoyer » dans son client de courriel.
 *
 * [decisions]: docs/11-decisions.md
 */
const val REPORTABLE_HISTORY = 1
