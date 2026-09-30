package app.hexavore.domain.usecase

import app.hexavore.core.testing.InMemoryProfiles
import app.hexavore.domain.profile.Activity
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.UnitSystem
import app.hexavore.domain.profile.UserProfile
import app.hexavore.domain.profile.WeeklySessions
import app.hexavore.domain.profile.WorkActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Le système d'unités, lu et choisi.
 *
 * **Une question, un endroit.** Le sélecteur d'une ligne, le journal de poids et l'écran
 * de profil la posent tous ; chacun lisant le profil pour son compte, chacun aurait
 * décidé ce que vaut un profil absent — et l'un d'eux aurait fini par décider autrement.
 */
class UnitSystemChoiceTest {
    private val profiles = InMemoryProfiles()

    @Test
    fun `sans profil, le metrique`() = runTest {
        // L etat d une installation neuve, avant l onboarding : il n y a pas encore de
        // personne a qui attribuer un choix.
        assertEquals(UnitSystem.METRIC, ObserveUnitSystem(profiles)().first())
    }

    @Test
    fun `le profil dit lequel`() = runTest {
        profiles.save(profil(UnitSystem.IMPERIAL))

        assertEquals(UnitSystem.IMPERIAL, ObserveUnitSystem(profiles)().first())
    }

    @Test
    fun `choisir n ecrit que le reglage`() = runTest {
        // Rien d autre ne bouge : ni le poids, ni la taille, ni l objectif. C est ce qui
        // permet de basculer, de regarder, et de revenir sans qu un chiffre ait change.
        profiles.save(profil(UnitSystem.METRIC))

        assertTrue(ChooseUnitSystem(profiles)(UnitSystem.IMPERIAL))

        val apres = profiles.observeProfile().first()!!
        assertEquals(UnitSystem.IMPERIAL, apres.unitSystem)
        assertEquals(profil(UnitSystem.IMPERIAL), apres, "seul le reglage a change, tout le reste est intact")
    }

    @Test
    fun `sans profil, choisir ne cree rien`() = runTest {
        // Inventer un profil vide pour y ranger une preference y ecrirait un poids et
        // une taille que personne n a donnes.
        assertFalse(ChooseUnitSystem(profiles)(UnitSystem.IMPERIAL))

        assertEquals(null, profiles.observeProfile().first())
    }

    private fun profil(system: UnitSystem) = UserProfile(
        birthDate = LocalDate.of(1990, 5, 4),
        sex = Sex.MALE,
        heightCm = 178.0,
        activity = Activity(WorkActivity.ON_FEET, WeeklySessions(3)),
        unitSystem = system,
    )
}
