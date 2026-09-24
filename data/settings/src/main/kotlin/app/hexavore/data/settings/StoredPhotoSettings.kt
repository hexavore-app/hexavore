package app.hexavore.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.diary.PhotoSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * Garde-t-on les photos ? Dans son propre fichier de préférences.
 *
 * **Allumé tant que personne n'a dit non.** Le défaut d'un fichier absent est la valeur
 * qu'aurait une installation neuve, et c'est aussi ce que l'écran des photos annonce.
 *
 * **Lu sans suspendre**, comme l'apparence : la réponse sert au moment où une analyse
 * se termine, et aller la chercher de façon asynchrone à ce moment-là ferait dépendre
 * l'écriture d'une course.
 */
internal class StoredPhotoSettings(
    private val preferences: SharedPreferences,
    private val dispatchers: DispatcherProvider,
) : PhotoSettings {
    private val keeping = MutableStateFlow(preferences.getBoolean(KEEPING, true))

    override fun observeKeeping(): Flow<Boolean> = keeping

    override suspend fun keeping(): Boolean = keeping.value

    override suspend fun setKeeping(keep: Boolean) = withContext(dispatchers.io) {
        preferences.edit { putBoolean(KEEPING, keep) }
        keeping.value = keep
    }
}

private const val KEEPING = "photos.keeping"
