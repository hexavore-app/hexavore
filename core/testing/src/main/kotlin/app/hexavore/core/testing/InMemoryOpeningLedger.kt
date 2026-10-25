package app.hexavore.core.testing

import app.hexavore.domain.opening.OpeningLedger
import app.hexavore.domain.opening.OpeningRecord
import app.hexavore.domain.opening.TipSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Le registre des ouvertures et l'interrupteur des astuces, en mémoire.
 *
 * Les deux dans un seul objet, comme le vrai, qui les range dans le même fichier : un
 * test qui éteint les astuces depuis la boîte d'une astuce doit voir l'écran des réglages
 * le dire.
 */
class InMemoryOpeningLedger(var record: OpeningRecord = OpeningRecord(), tipsEnabled: Boolean = true) :
    OpeningLedger,
    TipSettings {
    private val tips = MutableStateFlow(tipsEnabled)

    override suspend fun read(): OpeningRecord = record

    override suspend fun write(record: OpeningRecord) {
        this.record = record
    }

    override fun observe(): Flow<Boolean> = tips

    override suspend fun setEnabled(enabled: Boolean) {
        tips.value = enabled
    }
}
