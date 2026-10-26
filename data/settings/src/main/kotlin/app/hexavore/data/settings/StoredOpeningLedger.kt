package app.hexavore.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.opening.OpeningLedger
import app.hexavore.domain.opening.OpeningRecord
import app.hexavore.domain.opening.OpeningTip
import app.hexavore.domain.opening.TipSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Le registre des ouvertures et l'interrupteur des astuces ([D179][decisions]).
 *
 * **Dans le fichier des pastilles**, avec le souvenir du tour : c'est l'état de ce que
 * l'écran a déjà dit, pas une donnée personnelle, et rien à faire dans le fichier des
 * clés. Un effacement complet le vide avec le reste, et l'application repart comme une
 * installation neuve — astuces comprises.
 *
 * **Une clé par champ, nommée d'après lui**, comme les pastilles : le fichier se relit à
 * l'œil, et une astuce se range sous son nom. Un nom qu'aucune astuce ne porte plus —
 * celui d'une version d'avant — s'ignore à la lecture plutôt que de faire échouer
 * l'accueil.
 *
 * [decisions]: docs/11-decisions.md
 */
internal class StoredOpeningLedger(
    private val preferences: SharedPreferences,
    private val dispatchers: DispatcherProvider,
) : OpeningLedger,
    TipSettings {
    private val tips = MutableStateFlow(preferences.getBoolean(TIPS_ENABLED, true))

    override suspend fun read(): OpeningRecord = withContext(dispatchers.io) { preferences.readRecord() }

    override suspend fun write(record: OpeningRecord) = withContext(dispatchers.io) {
        preferences.edit { writeRecord(record) }
    }

    override fun observe(): Flow<Boolean> = tips

    override suspend fun setEnabled(enabled: Boolean) = withContext(dispatchers.io) {
        preferences.edit { putBoolean(TIPS_ENABLED, enabled) }
        tips.value = enabled
    }

    /** Repart comme une installation neuve. Appelé par l'effacement, comme les autres magasins. */
    internal suspend fun forget() = withContext(dispatchers.io) {
        preferences.edit { KEYS.forEach(::remove) }
        tips.value = true
    }
}

private fun SharedPreferences.readRecord(): OpeningRecord = OpeningRecord(
    openings = getInt(OPENINGS, 0),
    lastSeenAt = instant(LAST_SEEN),
    firstOpenedAt = instant(FIRST_OPENED),
    lastMessageAt = getInt(LAST_MESSAGE, NONE).takeIf { it != NONE },
    tipsShown = getStringSet(TIPS_SHOWN, emptySet()).orEmpty()
        .mapNotNull { name -> OpeningTip.entries.firstOrNull { it.name == name } }
        .toSet(),
    ratingClosed = getBoolean(RATING_CLOSED, false),
    ratingAfter = getInt(RATING_AFTER, 0),
)

private fun SharedPreferences.Editor.writeRecord(record: OpeningRecord) {
    putInt(OPENINGS, record.openings)
    putInstant(LAST_SEEN, record.lastSeenAt)
    putInstant(FIRST_OPENED, record.firstOpenedAt)
    putInt(LAST_MESSAGE, record.lastMessageAt ?: NONE)
    putStringSet(TIPS_SHOWN, record.tipsShown.mapTo(mutableSetOf()) { it.name })
    putBoolean(RATING_CLOSED, record.ratingClosed)
    putInt(RATING_AFTER, record.ratingAfter)
}

private fun SharedPreferences.instant(key: String): Instant? =
    if (contains(key)) Instant.ofEpochMilli(getLong(key, 0L)) else null

private fun SharedPreferences.Editor.putInstant(key: String, value: Instant?) {
    if (value == null) remove(key) else putLong(key, value.toEpochMilli())
}

private const val OPENINGS = "opening.count"
private const val LAST_SEEN = "opening.last_seen"
private const val FIRST_OPENED = "opening.first"
private const val LAST_MESSAGE = "opening.last_message"
private const val TIPS_SHOWN = "opening.tips_shown"
private const val TIPS_ENABLED = "opening.tips_enabled"
private const val RATING_CLOSED = "opening.rating_closed"
private const val RATING_AFTER = "opening.rating_after"

/** Ce qu'un effacement retire, et rien d'autre : le fichier porte aussi les pastilles et le tour. */
private val KEYS =
    listOf(OPENINGS, LAST_SEEN, FIRST_OPENED, LAST_MESSAGE, TIPS_SHOWN, TIPS_ENABLED, RATING_CLOSED, RATING_AFTER)

/** « Aucun message encore » : une ouverture ne porte jamais ce numéro. */
private const val NONE = -1
