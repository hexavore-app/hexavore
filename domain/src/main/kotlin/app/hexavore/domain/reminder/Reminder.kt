package app.hexavore.domain.reminder

import app.hexavore.domain.diary.MealMoment
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

/**
 * Ce qui a le droit de sonner **hors de l'application**.
 *
 * ### Ce qui change, et ce que ça coûte
 *
 * [Notice][app.hexavore.domain.notice.Notice] posait une règle nette : *rien ne sort de
 * l'application* — pas de notification système, pas de permission, pas de travail de
 * fond. Ces rappels la lèvent, et c'est une vraie dépense ([D134][decisions]) : une
 * permission à demander, un travail planifié à tenir, et surtout quelqu'un qu'on peut
 * agacer. Une notification coupée au niveau du système ne se rallume pas, et l'on perd
 * alors le canal entier.
 *
 * D'où la contrepartie, qui est la seule chose qui rende cette dépense tenable :
 * **aucun rappel ne sonne s'il n'a rien à dire**. Le rappel du déjeuner se tait quand
 * le déjeuner est déjà noté ; celui de la série se tait quand la journée est notée, ou
 * quand aucune série ne court. Un rappel qu'on peut prévoir est un rappel qu'on éteint.
 *
 * ### Pourquoi quatre
 *
 * Trois repas, parce que c'est là que la saisie se joue, et un rappel de fin de journée
 * pour la série. Le goûter n'en a pas : il n'est pas un repas que tout le monde prend,
 * et un rappel qui ne concerne pas la moitié des gens est un rappel qu'on désactive.
 *
 * [decisions]: docs/11-decisions.md
 */
enum class Reminder(
    /**
     * Le moment que ce rappel couvre, ou `null` pour celui de la série.
     *
     * C'est lui qui décide du silence : un rappel de repas se tait quand un plat de ce
     * moment existe déjà. Il s'appuie sur [MealMoment], qui range déjà les plats par
     * moment depuis [D118][decisions] — rien de neuf à décider, donc rien qui puisse
     * diverger du titre affiché sous chaque plat.
     *
     * [decisions]: docs/11-decisions.md
     */
    val moment: MealMoment?,
    /** L'heure à laquelle il sonne, tant que personne ne l'a déplacée. */
    val default: LocalTime,
) {
    BREAKFAST(moment = MealMoment.BREAKFAST, default = MORNING),
    LUNCH(moment = MealMoment.LUNCH, default = MIDDAY),
    DINNER(moment = MealMoment.DINNER, default = EVENING),

    /**
     * La série en danger, en fin de journée.
     *
     * **Le seul qui parle de la progression**, et le seul qui ait quelque chose à
     * perdre : une série de trente jours qui casse pour un soir d'oubli est exactement
     * ce qu'un rappel sert à éviter. Il se tait quand la journée est notée — il n'y a
     * alors plus rien en danger — et quand aucune série ne court, faute de quoi il
     * réclamerait une saisie à quelqu'un qui n'a rien commencé.
     */
    STREAK(moment = null, default = NIGHT),
}

/**
 * Les quatre heures par défaut.
 *
 * **Des heures de rappel, pas des heures de repas.** Elles se placent là où quelqu'un
 * qui n'a pas encore noté son repas l'a probablement déjà pris : un rappel de déjeuner
 * à midi pile arriverait pendant le repas, ce qui est le moment où l'on n'a pas le
 * téléphone en main.
 *
 * Elles ne sont écrites nulle part sur le disque tant que personne ne les déplace : les
 * changer ici déplace les rappels de tous ceux qui n'y ont jamais touché, ce qui est la
 * bonne lecture.
 */
private val MORNING: LocalTime = LocalTime.of(8, 0)
private val MIDDAY: LocalTime = LocalTime.of(12, 30)
private val EVENING: LocalTime = LocalTime.of(19, 30)

/** Assez tard pour que la journée soit jouée, assez tôt pour pouvoir encore la noter. */
private val NIGHT: LocalTime = LocalTime.of(21, 0)

/**
 * Quand chaque rappel sonne, et lesquels sonnent.
 *
 * **Tous allumés par défaut**, avec leurs heures par défaut. Un rappel éteint d'office
 * ne serait découvert par personne, et le réglage existe pour celui que l'un d'eux
 * agace — pas pour lui cacher les trois autres. C'est le même raisonnement que les
 * pastilles, appliqué à quelque chose qui coûte plus cher.
 */
data class ReminderSetup(
    val enabled: Set<Reminder> = Reminder.entries.toSet(),
    val times: Map<Reminder, LocalTime> = emptyMap(),
) {
    /** L'heure de ce rappel : celle qu'on a choisie, ou celle par défaut. */
    operator fun get(reminder: Reminder): LocalTime = times[reminder] ?: reminder.default

    /** Ce rappel est-il allumé ? */
    operator fun contains(reminder: Reminder): Boolean = reminder in enabled
}

/** Ce que l'utilisateur a réglé. */
interface ReminderSettings {
    fun observe(): Flow<ReminderSetup>

    suspend fun current(): ReminderSetup

    suspend fun setEnabled(reminder: Reminder, enabled: Boolean)

    suspend fun setTime(reminder: Reminder, time: LocalTime)

    /**
     * A-t-on déjà demandé la permission de notifier ?
     *
     * **Une fois, et une seule.** Android ne montre sa boîte que deux fois ; au-delà,
     * `launch` rend « refusé » sans rien afficher. Redemander à chaque lancement ne
     * ferait donc rien de visible, mais ce serait une demande qu'on ne contrôle plus —
     * et le jour où le système changerait d'avis, l'application harcèlerait.
     *
     * Elle se pose au dernier geste de l'onboarding, et **à la première ouverture de
     * l'accueil** pour qui avait déjà installé l'application avant que les rappels
     * existent ([D135][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    suspend fun permissionAsked(): Boolean

    suspend fun markPermissionAsked()
}

/**
 * Ce qui place les rappels dans le temps, hors de l'application.
 *
 * **Un port et non un appel direct** : planifier demande le système Android, et le
 * domaine n'en connaît rien. Ce qu'il sait dire est *quels rappels doivent courir, et à
 * quelle heure* ; comment cela survit à un redémarrage regarde l'adaptateur.
 */
interface ReminderScheduler {
    /** Reprend tout : ce qui est éteint s'annule, ce qui est allumé se replace. */
    suspend fun reschedule(setup: ReminderSetup)
}
