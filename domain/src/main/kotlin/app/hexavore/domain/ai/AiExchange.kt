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
 * Si l'on **accumule** les échanges.
 *
 * **Éteint par défaut**, à la différence des pastilles : celles-ci décrivent, celui-ci
 * retient. Ce qui retient ne s'allume pas tout seul.
 *
 * Il ne gouverne plus que la **profondeur** de l'historique, et non le fait d'enregistrer
 * ou non : le dernier échange est gardé dans tous les cas, faute de quoi le signalement
 * d'une proposition fausse part sans ce qu'il promet de porter ([REPORTABLE_HISTORY]).
 */
interface DebugSettings {
    fun observe(): Flow<Boolean>

    /**
     * L'état courant, **sans suspendre**.
     *
     * Même raison que [AiExchangeLog.record] : c'est un intercepteur qui pose la
     * question, et il la pose sur le fil d'OkHttp. La valeur est en mémoire — il n'y a
     * rien à lire sur un disque.
     */
    fun enabled(): Boolean

    suspend fun setEnabled(enabled: Boolean)
}

/**
 * Le nombre d'échanges conservés.
 *
 * Vingt : de quoi remonter une boucle d'outillage entière — trois tours, plus les
 * appels qui l'ont précédée — sans garder la séance de la veille.
 */
const val EXCHANGE_HISTORY = 20

/**
 * Ce qu'on garde **toujours**, mode de mise au point éteint.
 *
 * Un seul échange : celui qu'un signalement joint. Le bouton de signalement promet
 * d'envoyer « ce qui a été demandé à l'IA et ce qu'elle a répondu » ; sans cette
 * mémoire-là il n'envoyait que le corps du courriel, et la promesse était fausse pour
 * quiconque n'avait pas d'abord découvert le mode de mise au point ([D153][decisions]).
 *
 * **Un, et pas vingt.** La règle qui tenait le journal éteint par défaut — ce qui
 * retient ne s'allume pas tout seul — reste vraie de l'historique : rien ne
 * s'**accumule** sans qu'on l'ait demandé. Ce qui change est qu'on cesse de ne rien
 * retenir du tout, parce qu'une fonction de l'application en dépend.
 *
 * Il vit en mémoire, meurt avec le processus, ne part que si l'utilisateur appuie sur
 * « signaler » **puis** sur « envoyer » dans son client de courriel.
 *
 * [decisions]: docs/11-decisions.md
 */
const val REPORTABLE_HISTORY = 1
