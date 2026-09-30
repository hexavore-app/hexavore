package app.hexavore.data.profile

import app.hexavore.core.testing.firstAfter
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.Goal
import app.hexavore.domain.goal.GoalId
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.profile.Activity
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.UnitSystem
import app.hexavore.domain.profile.UserProfile
import app.hexavore.domain.profile.WeeklySessions
import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.profile.WorkActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Ce que **les deux** implémentations du profil doivent tenir.
 *
 * `Profiles`, `WeightLog` et `Goals` ont chacun deux implémentations depuis la
 * tranche 4, et aucune ne les éprouvait ensemble. C'est la configuration qui a produit
 * quatre défauts livrés : un faux plus indulgent que le vrai, des tests écrits contre
 * le faux, et un chemin que l'application emprunte sans que rien ne le parcoure
 * ([D53][decisions]).
 *
 * Ce jeu est écrit une fois et exécuté deux fois — sur les faux de `:core:testing` et
 * sur `RoomProfileStore` sous Robolectric. Une propriété que le faux s'autorise à ne
 * pas tenir devient une ligne rouge à côté d'une verte.
 *
 * **Ce qu'il ne prouve pas.** Ni l'affichage, ni ce qu'un `ViewModel` fait de ces
 * flux. Il ne prouve pas non plus le repli d'une énumération inconnue à la lecture :
 * les énumérations du domaine sont fermées, donc aucun appelant du port ne peut
 * soumettre une valeur que le mapper aurait à replier. Cette propriété-là n'appartient
 * pas au contrat mais à la sérialisation, et elle est éprouvée dans
 * `ProfileMapperTest`, qui écrit directement en base.
 *
 * [decisions]: docs/11-decisions.md
 */
abstract class ProfileStoreContract {
    /** Le magasin sous test, vide. Tout ce que les cas contiennent y entre par les ports. */
    protected abstract fun store(): ProfileStoreView

    // --- Le profil --------------------------------------------------------------

    @Test
    fun `sans onboarding, il n y a pas de profil`() = runBlocking {
        assertNull("un profil est apparu de nulle part", store().observeProfile().first())
    }

    @Test
    fun `un profil enregistre se relit entier`() = runBlocking {
        val magasin = store()

        magasin.save(PROFIL)

        // L'objet entier, et non trois champs choisis : ce qui se perd dans un
        // aller-retour se perd toujours dans le champ qu'on n'a pas regarde.
        assertEquals(PROFIL, magasin.observeProfile().first())
    }

    @Test
    fun `corriger le profil remplace, il n en cree pas un second`() = runBlocking {
        val magasin = store()
        magasin.save(PROFIL)

        magasin.save(PROFIL_CORRIGE)

        assertEquals("la correction n a pas remplace", PROFIL_CORRIGE, magasin.observeProfile().first())
    }

    @Test
    fun `le flux du profil se dement apres une correction`() = runBlocking {
        val magasin = store()
        magasin.save(PROFIL)

        val apres = magasin.observeProfile().firstAfter(
            write = { magasin.save(PROFIL_CORRIGE) },
            matching = { it == PROFIL_CORRIGE },
        )

        assertEquals(PROFIL_CORRIGE, apres)
    }

    // --- Le journal de poids ----------------------------------------------------

    @Test
    fun `sans pesee, il n y a ni derniere ni journal`() = runBlocking {
        val magasin = store()

        assertNull(magasin.observeLatest().first())
        assertEquals(emptyList<WeightEntry>(), magasin.observeHistory().first())
    }

    @Test
    fun `deux pesees le meme jour n en font qu une, et la derniere gagne`() = runBlocking {
        // Se peser trois fois un matin est courant. En garder trois donnerait trois
        // fois plus de poids a ce jour-la dans la moyenne mobile, c'est-a-dire
        // exactement le bruit qu'elle sert a retirer.
        val magasin = store()

        magasin.record(WeightEntry(LUNDI, POIDS_INITIAL))
        magasin.record(WeightEntry(LUNDI, POIDS_CORRIGE))

        assertEquals(listOf(WeightEntry(LUNDI, POIDS_CORRIGE)), magasin.observeHistory().first())
    }

    @Test
    fun `la derniere pesee est celle du jour le plus recent, pas la derniere ecrite`() = runBlocking {
        // C'est elle qui sert au calcul de l'objectif. Rattraper une pesee oubliee
        // la veille ne doit pas faire recalculer sur un poids perime.
        val magasin = store()

        magasin.record(WeightEntry(MERCREDI, POIDS_INITIAL))
        magasin.record(WeightEntry(LUNDI, POIDS_CORRIGE))

        assertEquals(WeightEntry(MERCREDI, POIDS_INITIAL), magasin.observeLatest().first())
    }

    @Test
    fun `les pesees sortent de la plus ancienne a la plus recente`() = runBlocking {
        val magasin = store()

        magasin.record(WeightEntry(LUNDI, POIDS_INITIAL))
        magasin.record(WeightEntry(MERCREDI, POIDS_CORRIGE))
        magasin.record(WeightEntry(MARDI, POIDS_INTERMEDIAIRE))

        // L'ordre de la courbe, et non celui d'une liste de recents : le journal de
        // poids se lit de gauche a droite, du passe vers aujourd'hui.
        assertEquals(
            listOf(LUNDI, MARDI, MERCREDI),
            magasin.observeHistory().first().map { it.date },
        )
    }

    @Test
    fun `le journal ne cache aucune pesee`() = runBlocking {
        // Le port ne borne plus : la moyenne mobile a besoin de la fenetre entiere, et
        // une limite oubliee la lui aurait amputee par la gauche -- silencieusement,
        // en rendant une moyenne calculee sur moins de jours qu'annonce.
        val magasin = store()
        magasin.record(WeightEntry(LUNDI, POIDS_INITIAL))
        magasin.record(WeightEntry(MARDI, POIDS_INTERMEDIAIRE))
        magasin.record(WeightEntry(MERCREDI, POIDS_CORRIGE))

        assertEquals(listOf(LUNDI, MARDI, MERCREDI), magasin.observeHistory().first().map { it.date })
    }

    @Test
    fun `le flux des pesees se dement apres une ecriture`() = runBlocking {
        val magasin = store()
        magasin.record(WeightEntry(LUNDI, POIDS_INITIAL))

        val apres = magasin.observeLatest().firstAfter(
            write = { magasin.record(WeightEntry(MARDI, POIDS_CORRIGE)) },
            matching = { it?.date == MARDI },
        )

        assertEquals(WeightEntry(MARDI, POIDS_CORRIGE), apres)
    }

    // --- Les objectifs ----------------------------------------------------------

    @Test
    fun `sans objectif, il n y en a ni de courant ni pour aujourd hui`() = runBlocking {
        val magasin = store()

        assertNull(magasin.observeCurrent().first())
        assertNull(magasin.observeGoalOn(MARDI).first())
    }

    @Test
    fun `un objectif enregistre devient le courant et se relit entier`() = runBlocking {
        val magasin = store()

        magasin.replace(PREMIER)

        assertEquals(PREMIER, magasin.observeCurrent().first())
    }

    @Test
    fun `remplacer clot le precedent, et il n en reste qu un de courant`() = runBlocking {
        val magasin = store()
        magasin.replace(PREMIER)

        magasin.replace(SECOND)

        assertEquals(SECOND, magasin.observeCurrent().first())
    }

    @Test
    fun `le precedent est clos a la date de debut du nouveau`() = runBlocking {
        val magasin = store()
        magasin.replace(PREMIER)

        magasin.replace(SECOND)

        val clos = magasin.observeGoalOn(DEBUT_SECOND.minusDays(1)).first()
        assertEquals("l ancien objectif n a pas ete clos", DEBUT_SECOND, clos?.endedAt)
    }

    @Test
    fun `le jour du remplacement appartient au nouvel objectif`() = runBlocking {
        // Borne de fin exclue. Sans cette convention, cette journee releverait des
        // deux objectifs et le resume dependrait de l'ordre de lecture.
        val magasin = store()
        magasin.replace(PREMIER)
        magasin.replace(SECOND)

        assertEquals(SECOND.id, magasin.observeGoalOn(DEBUT_SECOND).first()?.id)
    }

    @Test
    fun `la veille du remplacement appartient encore a l ancien`() = runBlocking {
        val magasin = store()
        magasin.replace(PREMIER)
        magasin.replace(SECOND)

        assertEquals(PREMIER.id, magasin.observeGoalOn(DEBUT_SECOND.minusDays(1)).first()?.id)
    }

    @Test
    fun `le jour de debut est inclus`() = runBlocking {
        val magasin = store()
        magasin.replace(PREMIER)

        assertEquals(PREMIER.id, magasin.observeGoalOn(DEBUT_PREMIER).first()?.id)
    }

    @Test
    fun `une journee anterieure au premier objectif n en a aucun`() = runBlocking {
        // Elle n'a rien a quoi se comparer, et lui appliquer l'objectif d'aujourd'hui
        // serait la juger sur une regle qu'elle n'avait pas.
        val magasin = store()
        magasin.replace(PREMIER)

        assertNull(magasin.observeGoalOn(DEBUT_PREMIER.minusDays(1)).first())
    }

    @Test
    fun `le flux de l objectif du jour se dement apres un remplacement`() = runBlocking {
        val magasin = store()
        magasin.replace(PREMIER)

        val apres = magasin.observeGoalOn(DEBUT_SECOND).firstAfter(
            write = { magasin.replace(SECOND) },
            matching = { it?.id == SECOND.id },
        )

        assertEquals(SECOND.id, apres?.id)
    }

    protected companion object {
        val LUNDI: LocalDate = LocalDate.of(2026, 8, 10)
        val MARDI: LocalDate = LocalDate.of(2026, 8, 11)
        val MERCREDI: LocalDate = LocalDate.of(2026, 8, 12)

        const val POIDS_INITIAL = 88.0
        const val POIDS_INTERMEDIAIRE = 87.4
        const val POIDS_CORRIGE = 87.2

        const val TAILLE_CM = 182.0
        const val TAILLE_CORRIGEE_CM = 183.0

        val PROFIL = UserProfile(
            birthDate = LocalDate.of(1991, 3, 4),
            sex = Sex.MALE,
            heightCm = TAILLE_CM,
            activity = Activity(WorkActivity.ON_FEET, WeeklySessions(3)),
            unitSystem = UnitSystem.METRIC,
        )

        /**
         * Le même profil, corrigé sur **tous** les champs éditables.
         *
         * Un seul champ modifié laisserait passer un aller-retour qui en perd un
         * autre : c'est le champ qu'on n'a pas regardé qui se perd.
         */
        val PROFIL_CORRIGE = UserProfile(
            birthDate = LocalDate.of(1990, 12, 25),
            sex = Sex.UNSPECIFIED,
            heightCm = TAILLE_CORRIGEE_CM,
            activity = Activity(WorkActivity.PHYSICAL, WeeklySessions(5)),
            unitSystem = UnitSystem.IMPERIAL,
        )

        val DEBUT_PREMIER: LocalDate = LocalDate.of(2026, 6, 1)
        val DEBUT_SECOND: LocalDate = LocalDate.of(2026, 7, 1)

        private val QUOTIDIEN = DailyGoal(
            kcal = 2000.0,
            protein = 112.0,
            carbs = 223.0,
            sugars = 50.0,
            fat = 67.0,
            fiber = 28.0,
        )

        val PREMIER = Goal(
            id = GoalId("goal-premier"),
            startedAt = DEBUT_PREMIER,
            origin = GoalOrigin.CALCULATED,
            strategy = GoalStrategy.MAINTAIN,
            daily = QUOTIDIEN,
        )

        /**
         * Un cap différent, avec ce que le premier n'a pas : une cible et une échéance.
         *
         * Et **saisi à la main tout en portant une cible**, ce qui n'est pas une
         * incohérence de fixture : depuis [D60][decisions], le poids visé et l'échéance
         * décrivent le cap annoncé, dont le journal de poids tire sa trajectoire, sans
         * piloter les six chiffres. C'est la combinaison la plus facile à perdre dans un
         * aller-retour, donc celle que le contrat fait voyager.
         *
         * [decisions]: docs/11-decisions.md
         */
        val SECOND = Goal(
            id = GoalId("goal-second"),
            startedAt = DEBUT_SECOND,
            origin = GoalOrigin.MANUAL,
            strategy = GoalStrategy.LOSE,
            targetWeightKg = 80.0,
            targetDate = LocalDate.of(2026, 12, 31),
            daily = QUOTIDIEN.copy(kcal = 1800.0),
        )
    }
}
