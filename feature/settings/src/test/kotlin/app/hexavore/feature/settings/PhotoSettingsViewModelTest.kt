package app.hexavore.feature.settings

import app.hexavore.core.testing.InMemoryDishPhotos
import app.hexavore.core.testing.InMemoryPhotoSettings
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.PhotoSettings
import app.hexavore.domain.diary.PhotoWeight
import app.hexavore.domain.usecase.ChoosePhotoKeeping
import app.hexavore.domain.usecase.ObservePhotoKeeping
import app.hexavore.domain.usecase.PurgeDishPhotos
import app.hexavore.domain.usecase.WeighDishPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Garder les photos des plats, voir ce qu'elles pèsent, et tout effacer. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class PhotoSettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val photos = InMemoryDishPhotos()
    private val settings = InMemoryPhotoSettings()

    @BeforeEach
    fun installer() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun retirer() = Dispatchers.resetMain()

    @Test
    fun `l'ecran montre le reglage et le poids des photos gardees`() = runTest(dispatcher) {
        garder(DishId("a"))
        garder(DishId("b"))

        val modele = modele()
        backgroundScope.launch { modele.uiState.collect { } }

        assertTrue(modele.uiState.value.keeping)
        assertEquals(2, modele.uiState.value.weight.count)
    }

    @Test
    fun `tout effacer vide le depot et remet le poids a zero`() = runTest(dispatcher) {
        garder(DishId("a"))
        val modele = modele()
        backgroundScope.launch { modele.uiState.collect { } }

        modele.onPurge()

        assertEquals(PhotoWeight.NOTHING, modele.uiState.value.weight, "le poids doit etre relu apres l'effacement")
        assertEquals(PhotoWeight.NOTHING, photos.weight())
    }

    @Test
    fun `ne plus garder s'enregistre`() = runTest(dispatcher) {
        val modele = modele()
        backgroundScope.launch { modele.uiState.collect { } }

        modele.onKeeping(false)

        assertFalse(settings.keeping())
        assertFalse(modele.uiState.value.keeping)
    }

    @Test
    fun `un reglage illisible retombe sur le defaut, pas sur un ecran vide`() = runTest(dispatcher) {
        val illisible = object : PhotoSettings {
            override fun observeKeeping(): Flow<Boolean> = flow { error("fichier abime") }

            override suspend fun keeping(): Boolean = error("fichier abime")

            override suspend fun setKeeping(keep: Boolean) = Unit
        }
        val modele = PhotoSettingsViewModel(
            ObservePhotoKeeping(illisible),
            ChoosePhotoKeeping(illisible, photos),
            WeighDishPhotos(photos),
            PurgeDishPhotos(photos),
        )
        backgroundScope.launch { modele.uiState.collect { } }

        assertTrue(modele.uiState.value.keeping)
    }

    private suspend fun garder(dish: DishId) {
        photos.stage(byteArrayOf(1))
        photos.attach(dish)
    }

    private fun modele() = PhotoSettingsViewModel(
        ObservePhotoKeeping(settings),
        ChoosePhotoKeeping(settings, photos),
        WeighDishPhotos(photos),
        PurgeDishPhotos(photos),
    )
}
