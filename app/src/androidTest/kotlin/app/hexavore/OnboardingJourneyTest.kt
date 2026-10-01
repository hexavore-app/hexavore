package app.hexavore

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import app.hexavore.feature.onboarding.R as OnboardingStrings

/**
 * Les deux premiers écrans d'une installation neuve.
 *
 * ### Pourquoi une classe à part
 *
 * [AppJourneyTest] écrit un profil et un objectif avant chaque test, sans quoi aucun de
 * ses écrans n'est atteignable. Or **c'est l'absence d'objectif** qui envoie sur
 * l'onboarding ([StartDestinationViewModel]) : les deux ne peuvent pas cohabiter dans la
 * même classe, et l'ordre des tests JUnit ne se commande pas.
 *
 * Ce fichier s'exécute donc **sur une installation fraîche**, avant l'autre — c'est ce
 * que fait le script de passage en revue, et c'est aussi l'ordre dans lequel un
 * utilisateur rencontre ces écrans.
 *
 * ### Ce qu'il tient
 *
 * L'accueil de l'onboarding, puis la première question. Pas les cinq : elles demandent
 * des sélecteurs de date et des champs numériques, et un test qui les traverserait
 * casserait à la première question déplacée sans rien dire de la version d'Android. Les
 * deux premiers écrans suffisent à prouver que le chemin d'entrée s'ouvre
 * ([D139][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@RunWith(AndroidJUnit4::class)
class OnboardingJourneyTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    @Test
    fun l_onboarding_accueille_puis_demande_qui_vous_etes() {
        ActivityScenario.launch(MainActivity::class.java)
        attend(OnboardingStrings.string.onboarding_welcome_title)
        attend(OnboardingStrings.string.onboarding_disclaimer)

        // La case d'avertissement, puis  Continuer  : le bouton reste inerte tant que
        // la case n'est pas cochee, et c'est exactement ce qu'on veut verifier.
        clique(texte(OnboardingStrings.string.onboarding_disclaimer))
        clique(texte(OnboardingStrings.string.onboarding_next))

        attend(OnboardingStrings.string.onboarding_you_title)
        // La phrase de confidentialite est une contrainte ferme de docs/01 : elle doit
        // etre lisible a l'endroit ou l'on commence a saisir, sur toutes les versions.
        attend(OnboardingStrings.string.onboarding_privacy)
    }

    private fun texte(id: Int): String = context.getString(id)

    private fun attend(id: Int) {
        val libelle = texte(id)
        repeat(ESSAIS) { if (voit(libelle)) return }
        fail("L'onboarding n'a jamais montre : $libelle")
    }

    private fun voit(libelle: String): Boolean =
        device.wait(Until.hasObject(By.textContains(libelle)), PAS_MS) == true ||
            device.wait(Until.hasObject(By.descContains(libelle)), PAS_MS) == true

    private fun clique(libelle: String) {
        val cible = device.findObject(By.textContains(libelle)) ?: device.findObject(By.descContains(libelle))
        requireNotNull(cible) { "Introuvable : $libelle" }.click()
        device.waitForIdle(PAS_MS)
    }

    private companion object {
        const val ESSAIS = 20
        const val PAS_MS = 500L
    }
}
