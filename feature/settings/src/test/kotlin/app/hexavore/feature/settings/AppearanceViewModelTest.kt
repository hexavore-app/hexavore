package app.hexavore.feature.settings

import app.hexavore.core.testing.InMemoryAppearanceSettings
import app.hexavore.core.testing.InMemoryProfiles
import app.hexavore.domain.appearance.DishDisplayStyle
import app.hexavore.domain.appearance.ThemeMode
import app.hexavore.domain.profile.ActivityLevel
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.UnitSystem
import app.hexavore.domain.profile.UserProfile
import app.hexavore.domain.usecase.ChooseUnitSystem
import app.hexavore.domain.usecase.ObserveDishStyle
import app.hexavore.domain.usecase.ObserveUnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Le thème, lu et écrit.
 *
 * **Le défaut est « suivre le système »**, et c'est la règle qui casserait le plus
 * discrètement : quelqu'un qui installe la mise à jour ne doit pas voir son application
 * changer de couleur parce qu'un réglage neuf a pris une valeur arbitraire.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AppearanceViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val settings = InMemoryAppearanceSettings()
    private val profils = InMemoryProfiles()

    @BeforeEach
    fun installer() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun retirer() = Dispatchers.resetMain()

    @Test
    fun `sans rien de choisi, l ecran suit le systeme`() = runTest(dispatcher) {
        assertEquals(ThemeMode.SYSTEM, modele().uiState.value.theme)
    }

    @Test
    fun `choisir un theme l enregistre et le montre`() = runTest(dispatcher) {
        val modele = modele()

        modele.onTheme(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, settings.current, "le magasin garde le choix")
        assertEquals(ThemeMode.DARK, modele.uiState.value.theme, "l ecran le montre")
    }

    @Test
    fun `revenir au systeme est un choix comme un autre`() = runTest(dispatcher) {
        // Et non un effacement : « suivre le systeme » se range explicitement, sans
        // quoi il faudrait savoir ce qu'un vide veut dire pour le relire.
        val modele = modele()

        modele.onTheme(ThemeMode.LIGHT)
        modele.onTheme(ThemeMode.SYSTEM)
        advanceUntilIdle()

        assertEquals(ThemeMode.SYSTEM, settings.current)
    }

    @Test
    fun `un magasin d un autre theme s affiche tel quel`() = runTest(dispatcher) {
        val modele = modeleDe(InMemoryAppearanceSettings(initial = ThemeMode.LIGHT))
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, modele.uiState.value.theme)
    }

    @Test
    fun `un magasin illisible laisse l ecran ouvert, sur le systeme`() = runTest(dispatcher) {
        // L apparence est un confort : rien n y justifie de refuser l ecran a quelqu un
        // dont le fichier de preferences a ete abime. C est ce repli qui donne son sens
        // au defaut de l etat -- sans lui, il ne serait jamais observe.
        val modele = modeleDe(InMemoryAppearanceSettings(initial = ThemeMode.DARK, failure = true))
        advanceUntilIdle()

        assertEquals(ThemeMode.SYSTEM, modele.uiState.value.theme)
    }

    @Test
    fun `sans profil, l ecran montre le metrique`() = runTest(dispatcher) {
        // L etat d une installation neuve : le reglage vit sur le profil, et il n y en
        // a pas encore.
        assertEquals(UnitSystem.METRIC, modele().uiState.value.units)
    }

    @Test
    fun `choisir un systeme l ecrit sur le profil`() = runTest(dispatcher) {
        profils.save(PROFIL)
        val modele = modele()

        modele.onUnits(UnitSystem.IMPERIAL)
        advanceUntilIdle()

        assertEquals(UnitSystem.IMPERIAL, profils.observeProfile().first()!!.unitSystem)
        assertEquals(UnitSystem.IMPERIAL, modele.uiState.value.units)
    }

    @Test
    fun `sans profil, choisir ne fait rien et ne casse rien`() = runTest(dispatcher) {
        // L ecran est atteignable avant l onboarding en theorie ; il ne doit ni creer un
        // profil vide, ni tomber.
        val modele = modele()

        modele.onUnits(UnitSystem.IMPERIAL)
        advanceUntilIdle()

        assertEquals(UnitSystem.METRIC, modele.uiState.value.units)
    }

    private fun modele() = modeleDe(settings)

    @Test
    fun `sans rien de choisi, les plats s affichent en simplifie`() = runTest(dispatcher) {
        // Le defaut d'une installation neuve : le detaille cite chaque aliment de
        // chaque plat, ce qui fait beaucoup de texte des qu'une journee est chargee.
        assertEquals(DishDisplayStyle.SIMPLE, modele().uiState.value.dishStyle)
    }

    @Test
    fun `choisir le detaille l enregistre et le montre`() = runTest(dispatcher) {
        val modele = modele()

        modele.onDishStyle(DishDisplayStyle.DETAILED)
        advanceUntilIdle()

        assertEquals(DishDisplayStyle.DETAILED, modele.uiState.value.dishStyle)
        assertEquals(
            DishDisplayStyle.DETAILED,
            settings.currentStyle,
            "le choix doit etre ecrit, pas seulement affiche",
        )
    }

    @Test
    fun `un magasin illisible laisse l ecran ouvert, sur le simplifie`() = runTest(dispatcher) {
        // Le detaille serait le pire des deux replis : plus long a lire, et un reglage
        // qu'on croit avoir mis se retrouverait ignore sans un mot.
        val abime = InMemoryAppearanceSettings(initialStyle = DishDisplayStyle.DETAILED, failure = true)

        assertEquals(DishDisplayStyle.SIMPLE, modeleDe(abime).uiState.value.dishStyle)
    }

    /** Le profil porte les unites, le magasin porte le theme et le style : deux sources, un ecran. */
    private fun modeleDe(apparence: InMemoryAppearanceSettings) = AppearanceViewModel(
        settings = apparence,
        chooseUnits = ChooseUnitSystem(profils),
        observeUnits = ObserveUnitSystem(profils),
        observeDishStyle = ObserveDishStyle(apparence),
    )

    private companion object {
        val PROFIL = UserProfile(
            birthDate = LocalDate.of(1990, 5, 4),
            sex = Sex.MALE,
            heightCm = 178.0,
            activityLevel = ActivityLevel.MODERATE,
        )
    }
}
