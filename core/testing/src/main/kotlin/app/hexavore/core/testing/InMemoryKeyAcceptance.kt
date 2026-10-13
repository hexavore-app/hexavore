package app.hexavore.core.testing

import app.hexavore.domain.ai.AcceptedKey
import app.hexavore.domain.ai.KeyAcceptance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * La nouvelle d'une clé acceptée, pour les cas qui la lisent ou l'écrivent.
 *
 * La même mécanique que `PendingKeyAcceptance` — un emplacement, une nouvelle qui chasse
 * la précédente — avec de quoi vérifier sans passer par le flux. Elle est recopiée ici
 * plutôt qu'empruntée parce que les modules d'écran ne voient pas `:core:common` dans
 * leurs tests : c'est la forme qu'ont déjà prise les échanges avec l'IA.
 */
class InMemoryKeyAcceptance : KeyAcceptance {
    private val pending = MutableStateFlow<AcceptedKey?>(null)

    /** Ce qui attend d'être dit, pour qu'un cas vérifie sans s'abonner. */
    val current: AcceptedKey? get() = pending.value

    override fun observe(): Flow<AcceptedKey?> = pending

    override fun announce(key: AcceptedKey) {
        pending.value = key
    }

    override fun consume() {
        pending.value = null
    }
}
