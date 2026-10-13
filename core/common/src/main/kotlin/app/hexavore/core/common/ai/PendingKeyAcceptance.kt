package app.hexavore.core.common.ai

import app.hexavore.domain.ai.AcceptedKey
import app.hexavore.domain.ai.KeyAcceptance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * La nouvelle d'une clé acceptée, en mémoire, jusqu'à ce qu'un écran la dise.
 *
 * Elle vit ici, à côté du jour regardé et du journal des échanges, pour la même raison
 * qu'eux : c'est un état d'application qui n'appartient à aucun domaine de données. Un
 * module `:data` sous-entendrait un rangement, et c'est précisément ce qu'elle n'a pas
 * — elle dure le temps d'un retour en arrière.
 *
 * **Un seul emplacement**, et une nouvelle chasse la précédente : deux clés validées
 * coup sur coup n'ont qu'une chose à dire, celle qui sert maintenant.
 */
@Singleton
class PendingKeyAcceptance @Inject constructor() : KeyAcceptance {
    private val pending = MutableStateFlow<AcceptedKey?>(null)

    override fun observe(): Flow<AcceptedKey?> = pending

    override fun announce(key: AcceptedKey) {
        pending.value = key
    }

    override fun consume() {
        pending.value = null
    }
}
