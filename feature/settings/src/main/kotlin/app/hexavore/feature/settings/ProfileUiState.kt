package app.hexavore.feature.settings

import androidx.compose.runtime.Immutable
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.profile.Activity
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.UnitSystem
import app.hexavore.domain.profile.UserProfile
import app.hexavore.domain.profile.WeeklySessions
import app.hexavore.domain.profile.WorkActivity
import app.hexavore.domain.usecase.GoalPlan
import app.hexavore.domain.usecase.GoalRequest
import java.time.LocalDate

/**
 * Ce que l'écran affiche et laisse corriger.
 *
 * Tout est nullable **bien que rien ne le soit au chargement** : un champ se vide, et
 * un formulaire qui refuserait de représenter cet état obligerait à réécrire dedans un
 * chiffre que l'utilisateur vient d'effacer.
 *
 * [manual] est le mode, et c'est lui qui décide de tout le reste ([D60][decisions]).
 * En calculé, les six chiffres viennent du profil et de l'échéance ; en manuel, ils
 * viennent de [macros] et plus rien ne les recalcule.
 *
 * [decisions]: docs/11-decisions.md
 */
@Immutable
internal data class ProfileForm(
    val birthDate: LocalDate? = null,
    val sex: Sex? = null,
    val heightCm: Double? = null,
    val currentWeightKg: Double? = null,
    /** Le métier, et le sport qui s'y ajoute ([D137][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    val work: WorkActivity? = null,
    val sessions: WeeklySessions = WeeklySessions.NONE,
    val strategy: GoalStrategy? = null,
    val targetWeightKg: Double? = null,
    val targetDate: LocalDate? = null,
    val manual: Boolean = false,
    /** Les six chiffres saisis, en mode manuel. Une valeur nulle est un champ vidé. */
    val macros: Map<Macro, Double?> = emptyMap(),
) {
    /**
     * Ce sans quoi aucun calcul n'est possible.
     *
     * Même règle que la deuxième des cinq questions, réécrite ici plutôt que partagée :
     * l'onboarding la porte sur son propre type de réponses, qui compte en plus un
     * avertissement à accepter et une étape courante. Les mettre en commun demanderait
     * de remanier ce type-là, ce dont cette tranche n'a pas besoin — c'est une
     * duplication choisie, écrite en [D59][decisions].
     *
     * [decisions]: docs/11-decisions.md
     */
    val identityComplete: Boolean
        get() = birthDate != null && sex != null && heightCm != null && currentWeightKg != null

    /**
     * Le poids visé et l'échéance sont-ils exigés ?
     *
     * En calculé, oui — sans eux l'écart calorique vaudrait zéro et « perdre du poids »
     * produirait un objectif de maintien sous une étiquette qui ment. En **manuel**,
     * non : ils décrivent le cap annoncé mais ne pilotent aucun des six chiffres, et
     * exiger une date qui ne sert à rien serait exiger pour la forme ([D60][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    val horizonRequired: Boolean get() = !manual && strategy != null && strategy != GoalStrategy.MAINTAIN

    val horizonComplete: Boolean get() = !horizonRequired || (targetWeightKg != null && targetDate != null)

    /** Les six chiffres saisis, ou `null` si l'un d'eux manque. */
    fun typedGoal(): DailyGoal? = Macro.entries.fold<Macro, DailyGoal?>(EMPTY_GOAL) { goal, macro ->
        goal?.let { current -> macros[macro]?.let { current.with(macro, it) } }
    }
}

private val EMPTY_GOAL = DailyGoal(kcal = 0.0, protein = 0.0, carbs = 0.0, sugars = 0.0, fat = 0.0, fiber = 0.0)

/** Ce qui empêche d'enregistrer. L'écran le traduit en phrase, l'état ne connaît pas les ressources. */
internal enum class ProfileBlocker {
    IDENTITY,
    ACTIVITY,
    STRATEGY,
    HORIZON,
    EMPTY_MACRO,
}

/**
 * L'état de l'écran « Profil et objectifs ».
 *
 * [editing] est la première chose que l'écran regarde : **on consulte par défaut**, et
 * le crayon est la seule porte vers la modification ([D60][decisions]). Un écran de
 * réglages en édition permanente invite à corriger ce qu'on venait seulement relire.
 *
 * [plan] est recalculé à chaque correction et vaut `null` tant qu'un champ manque.
 * En mode manuel il continue d'être calculé — c'est ce qui permet de revenir au calcul
 * sans quitter l'écran — mais il ne décide plus des six chiffres.
 *
 * [decisions]: docs/11-decisions.md
 */
@Immutable
internal data class ProfileUiState(
    /** La journée de l'horloge, portée par l'état plutôt que relue par l'écran. */
    val today: LocalDate,
    val loaded: Boolean = false,
    /** La lecture du profil ou de l'objectif a échoué ([D39][decisions]). */
    val unreadable: Boolean = false,
    val editing: Boolean = false,
    /**
     * Le système d'unités du profil lu.
     *
     * **Porté par l'état et non relu par l'écran**, comme la journée de l'horloge : la
     * lecture a déjà le profil en main, et un second abonnement pour une propriété du
     * même objet ferait deux sources pour un seul fait.
     */
    val units: UnitSystem = UnitSystem.METRIC,
    val form: ProfileForm = ProfileForm(),
    /** Les six chiffres tels qu'ils sont **enregistrés**. Ce à quoi la confirmation compare. */
    val saved: DailyGoal? = null,
    val plan: GoalPlan? = null,
    /** La correction en attente de confirmation, `null` le reste du temps. */
    val pending: DailyGoal? = null,
    val saving: Boolean = false,
    val failed: Boolean = false,
) {
    /**
     * Ce qui manque, ou `null` si l'écran peut enregistrer.
     *
     * Une seule règle, lue à deux endroits : le `ViewModel` refuse d'écrire, l'écran
     * s'en sert pour dire quoi. Les deux interrogent cette propriété, donc ils ne
     * peuvent pas diverger.
     */
    val blocker: ProfileBlocker?
        get() = when {
            !form.identityComplete -> ProfileBlocker.IDENTITY
            form.work == null -> ProfileBlocker.ACTIVITY
            form.strategy == null -> ProfileBlocker.STRATEGY
            !form.horizonComplete -> ProfileBlocker.HORIZON
            form.manual && form.typedGoal() == null -> ProfileBlocker.EMPTY_MACRO
            else -> null
        }

    val canSave: Boolean get() = blocker == null && !saving

    /**
     * Les six chiffres tels qu'ils seront enregistrés.
     *
     * Manuel : ce qui a été tapé. Calculé : ce que le profil produit. C'est la seule
     * fois où le mode se lit dans le code de l'écran, et tout le reste en découle.
     */
    val daily: DailyGoal? get() = if (form.manual) form.typedGoal() else plan?.goal

    val origin: GoalOrigin get() = if (form.manual) GoalOrigin.MANUAL else GoalOrigin.CALCULATED
}

/**
 * `null` tant qu'une réponse manque : le calcul ne s'invente pas de valeurs par défaut.
 *
 * Quatre absences, en deux marches plutôt qu'en une conjonction : c'est le seuil de
 * complexité de condition de detekt, et la réponse est de le respecter plutôt que de le
 * relever ([docs/10][qualite]).
 *
 * [qualite]: docs/10-qualite-et-livraison.md
 */
internal fun ProfileForm.toProfile(): UserProfile? = when {
    birthDate == null || sex == null -> null
    heightCm == null || work == null -> null
    else -> UserProfile(
        birthDate = birthDate,
        sex = sex,
        heightCm = heightCm,
        activity = Activity(work = work, sessions = sessions),
    )
}

/**
 * La demande, telle que le calcul et l'enregistrement la lisent.
 *
 * Le poids visé et l'échéance y figurent **même en mode manuel**, où ils ne pilotent
 * rien : ils décrivent le cap annoncé, et c'est de là que le journal de poids tirera sa
 * trajectoire ([D60][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
internal fun ProfileForm.toRequest(): GoalRequest? =
    if (currentWeightKg != null && strategy != null && horizonComplete) {
        GoalRequest(
            strategy = strategy,
            currentWeightKg = currentWeightKg,
            targetWeightKg = targetWeightKg,
            targetDate = targetDate,
        )
    } else {
        null
    }
