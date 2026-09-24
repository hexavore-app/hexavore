package app.hexavore.feature.scan

import app.hexavore.core.testing.InMemoryBarcodeLookup
import app.hexavore.core.testing.InMemoryDishPhotos
import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.core.testing.InMemoryPhotoSettings
import app.hexavore.domain.food.Barcode
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.food.ProductLookup
import app.hexavore.domain.food.ProductSource
import app.hexavore.domain.nutrition.NutrientValues
import app.hexavore.domain.usecase.LookupBarcode
import app.hexavore.domain.usecase.StageDishPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * La seule part de l'écran de scan qui s'éprouve sans appareil.
 *
 * L'aperçu, la permission, la lampe et le retour haptique n'ont aucun équivalent sur
 * la JVM. Ce qui reste — l'enchaînement des quatre états, et le refus de relancer une
 * recherche déjà lancée — est justement ce qui casserait sans se voir.
 */
class ScanViewModelTest {
    @BeforeEach
    fun poserLeDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun rendreLeDispatcher() = Dispatchers.resetMain()

    @Test
    fun `un produit connu du catalogue mene a sa fiche`() = runTest {
        val viewModel = scanViewModel(catalogue = InMemoryFoodCatalog(initial = listOf(JUS)))

        viewModel.onBarcode(CODE)

        assertEquals(ScanUiState.Found(JUS.id), viewModel.uiState.value)
    }

    @Test
    fun `un produit qu Open Food Facts ignore laisse le code a l ecran`() = runTest {
        // C'est le code lu que l'ecran affiche et que la creation reprendra : sans
        // lui, « creer cet aliment » repartirait sans code-barres et le produit
        // resterait un cas particulier a chaque scan.
        val viewModel = scanViewModel(distant = ProductSource { ProductLookup.Unknown })

        viewModel.onBarcode(CODE)

        assertEquals(ScanUiState.Unknown(CODE), viewModel.uiState.value)
    }

    @Test
    fun `un service injoignable ne se confond pas avec un produit absent`() = runTest {
        // Les deux ouvrent le meme geste, et ne disent pas la meme chose : l'un
        // affirme une absence, l'autre dit que la question reste posee.
        val viewModel = scanViewModel(distant = ProductSource { ProductLookup.Unreachable })

        viewModel.onBarcode(CODE)

        assertEquals(ScanUiState.Unreachable(CODE), viewModel.uiState.value)
    }

    @Test
    fun `un catalogue illisible aboutit a la meme porte de sortie`() = runTest {
        // Une base qui refuse de se lire n'est pas un produit absent, mais le geste
        // utile est le meme : creer la fiche a la main. Sans ce repli, l'exception
        // remonterait et l'ecran resterait bloque sur « Recherche… ».
        val viewModel = scanViewModel(catalogue = InMemoryFoodCatalog(failure = true))

        viewModel.onBarcode(CODE)

        assertEquals(ScanUiState.Unreachable(CODE), viewModel.uiState.value)
    }

    @Test
    fun `une seconde lecture pendant la recherche est ignoree`() = runTest {
        // L'anti-rebond se tait deja apres une confirmation, mais l'ecran ne peut pas
        // en dependre : la garde vit aussi ici, pour survivre a un changement de
        // decodeur. Sans elle, deux codes lus coup sur coup ouvriraient deux fiches.
        val viewModel = scanViewModel(catalogue = InMemoryFoodCatalog(initial = listOf(JUS)))
        viewModel.onBarcode(CODE)

        viewModel.onBarcode(requireNotNull(Barcode.of("3017620422003")))

        assertEquals(ScanUiState.Found(JUS.id), viewModel.uiState.value)
    }

    @Test
    fun `reprendre rouvre la lecture et fait avancer la cle de reprise`() = runTest {
        // Un compteur et non un booleen : rescanner le **meme** produit doit
        // remarcher, et un booleen repasse a la meme valeur ne relance aucun effet.
        val viewModel = scanViewModel(distant = ProductSource { ProductLookup.Unknown })
        viewModel.onBarcode(CODE)
        val avant = viewModel.resumeKey.value

        viewModel.onResume()

        assertEquals(ScanUiState.Scanning, viewModel.uiState.value)
        assertEquals(avant + 1, viewModel.resumeKey.value)
    }

    // --- Decor -------------------------------------------------------------------

    private fun scanViewModel(
        catalogue: InMemoryFoodCatalog = InMemoryFoodCatalog(),
        distant: ProductSource = ProductSource { ProductLookup.Unreachable },
    ) = ScanViewModel(
        LookupBarcode(
            catalogue = InMemoryBarcodeLookup(catalogue),
            products = distant,
            store = catalogue,
        ),
        StageDishPhoto(photos, InMemoryPhotoSettings()),
    )

    /** Le depot des photos, pour verifier que la trame figee y arrive. */
    private val photos = InMemoryDishPhotos()

    private companion object {
        val CODE: Barcode = requireNotNull(Barcode.of("5449000000996"))

        val JUS = Food(
            id = FoodId("f-jus"),
            source = FoodSource.OFF,
            sourceRef = CODE.value,
            name = "Boisson gazeuse",
            per100g = NutrientValues(kcal = 42.0),
        )
    }
}
