package app.hexavore.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.hexavore.core.testing.TestDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La case de la tendance sous la courbe de poids : allumée d'abord, puis retenue (D159).
 *
 * Sur de vraies `SharedPreferences`, sous Robolectric : ce qui s'éprouve est qu'un
 * choix **survit** à l'objet qui l'a écrit — c'est-à-dire à la fermeture de l'écran, et
 * au redémarrage de l'application. Une case qu'on recoche à chaque visite n'est pas un
 * réglage.
 */
@RunWith(RobolectricTestRunner::class)
class StoredAppearanceSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dispatchers = TestDispatchers(UnconfinedTestDispatcher())
    private val file = context.getSharedPreferences("appearance-test", Context.MODE_PRIVATE)

    @Test
    fun `la tendance est tracee tant que rien n a ete choisi`() = runTest {
        assertEquals(true, StoredAppearanceSettings(file, dispatchers).observeWeightTrend().first())
    }

    @Test
    fun `le choix de la case survit a l objet qui l a ecrit`() = runTest {
        StoredAppearanceSettings(file, dispatchers).setWeightTrend(shown = false)

        // Un second magasin sur le meme fichier : c'est ce qu'est un redemarrage.
        assertEquals(false, StoredAppearanceSettings(file, dispatchers).observeWeightTrend().first())
    }
}
