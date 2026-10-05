package app.hexavore.core.common.ai

import app.hexavore.domain.ai.AiExchange
import app.hexavore.domain.ai.AiExchangeLog
import app.hexavore.domain.ai.DebugSettings
import app.hexavore.domain.ai.EXCHANGE_HISTORY
import app.hexavore.domain.ai.REPORTABLE_HISTORY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Les derniers échanges avec un fournisseur, **en mémoire et nulle part ailleurs**.
 *
 * Il vit ici, à côté de l'horloge et du jour regardé, pour la même raison qu'eux :
 * c'est un état d'application qui n'appartient à aucun domaine de données. Un module
 * `:data` sous-entendrait un rangement, et il n'y en a pas — c'est le point.
 *
 * **Le plus récent d'abord**, parce que celui qu'on vient de provoquer est celui qu'on
 * vient regarder.
 *
 * **Deux profondeurs, et une seule règle.** Le mode de mise au point allumé, on garde
 * [EXCHANGE_HISTORY] échanges — de quoi remonter une boucle d'outillage entière. Éteint,
 * on en garde **un** : celui qu'un signalement joint ([REPORTABLE_HISTORY]). Ne rien
 * garder du tout rendait le bouton de signalement menteur, puisqu'il promet d'envoyer
 * l'échange ([D153][decisions]).
 *
 * La profondeur se relit **à chaque écriture** et non une fois pour toutes : éteindre le
 * mode de mise au point doit faire retomber l'historique, pas le figer à vingt.
 *
 * [decisions]: docs/11-decisions.md
 *
 * `MutableStateFlow` et non une liste synchronisée : les écritures viennent du fil
 * d'OkHttp et la lecture d'un écran, donc de deux fils différents. `update` est
 * atomique, et l'écran reçoit la nouvelle liste sans avoir à relire.
 */
@Singleton
class RecentExchanges @Inject constructor(private val debug: DebugSettings) : AiExchangeLog {
    private val exchanges = MutableStateFlow<List<AiExchange>>(emptyList())

    override fun observe(): Flow<List<AiExchange>> = exchanges

    override fun record(exchange: AiExchange) {
        val profondeur = if (debug.enabled()) EXCHANGE_HISTORY else REPORTABLE_HISTORY
        exchanges.update { (listOf(exchange) + it).take(profondeur) }
    }

    override fun clear() {
        exchanges.value = emptyList()
    }
}
