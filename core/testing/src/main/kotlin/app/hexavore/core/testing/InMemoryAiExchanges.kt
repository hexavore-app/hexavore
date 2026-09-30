package app.hexavore.core.testing

import app.hexavore.domain.ai.AiExchange
import app.hexavore.domain.ai.AiExchangeLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Le journal des échanges, en mémoire.
 *
 * **Le vrai l'est aussi** : rien n'est écrit sur le disque, un fichier de mise au point
 * qui survit à la session finissant dans une sauvegarde. Ce faux ne simplifie donc rien
 * du comportement — il retire seulement la borne de la file, dont aucun test ne parle.
 *
 * L'ordre est celui de l'arrivée : c'est `lastOrNull()` qui désigne le dernier échange,
 * et c'est ce que le signalement joint ([D138][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
class InMemoryAiExchanges(initial: List<AiExchange> = emptyList()) : AiExchangeLog {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<List<AiExchange>> = state.asStateFlow()

    override fun record(exchange: AiExchange) {
        state.value = state.value + exchange
    }

    override fun clear() {
        state.value = emptyList()
    }
}
