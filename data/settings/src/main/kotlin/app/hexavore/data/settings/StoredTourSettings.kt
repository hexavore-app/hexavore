package app.hexavore.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.tour.TourSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * Le souvenir du tour guidé.
 *
 * **Dans le fichier des pastilles**, avec ce qui décrit l'état de l'écran plutôt que des
 * secrets : le tour n'est pas une donnée personnelle, et il n'a rien à faire dans le
 * fichier des clés — effacer sa clé d'IA ne devrait pas rejouer un tour qu'on a vu.
 *
 * Un effacement complet, lui, le ramène : c'est une installation neuve, et elle se
 * raconte comme telle.
 */
internal class StoredTourSettings(
    private val preferences: SharedPreferences,
    private val dispatchers: DispatcherProvider,
) : TourSettings {
    private val state = MutableStateFlow(preferences.getBoolean(TOUR_SEEN, false))

    override fun observeSeen(): Flow<Boolean> = state

    override suspend fun markSeen() = withContext(dispatchers.io) {
        preferences.edit { putBoolean(TOUR_SEEN, true) }
        state.value = true
    }

    /** Repart comme une installation neuve : le tour se remontrera. */
    internal suspend fun forget() = withContext(dispatchers.io) {
        preferences.edit { remove(TOUR_SEEN) }
        state.value = false
    }
}

private const val TOUR_SEEN = "tour.seen"
