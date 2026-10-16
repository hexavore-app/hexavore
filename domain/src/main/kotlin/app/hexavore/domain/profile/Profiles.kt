package app.hexavore.domain.profile

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Le profil, unique.
 *
 * `null` tant que l'onboarding n'a pas eu lieu — c'est ce qui décide de l'écran
 * d'ouverture, et c'est une information, pas un cas d'erreur.
 */
interface Profiles {
    fun observeProfile(): Flow<UserProfile?>

    suspend fun save(profile: UserProfile)
}

/** Une pesée. Une par jour : la dernière du jour remplace la précédente. */
data class WeightEntry(val date: LocalDate, val weightKg: Double)

/**
 * Le journal de poids.
 *
 * **Une pesée par jour, et la dernière gagne.** Se peser trois fois un matin est
 * courant ; en garder trois fausserait la moyenne mobile en donnant trois fois plus de
 * poids à ce jour-là qu'aux autres.
 *
 * Le poids brut est inexploitable — ±2 kg d'un jour à l'autre selon l'hydratation, le
 * sel, le transit — mais c'est lui qu'on stocke : le lissage est un calcul, et un
 * calcul se refait quand sa règle change ([docs/03][calculs]).
 *
 * [calculs]: docs/03-nutrition-calculs.md
 */
interface WeightLog {
    /**
     * Toutes les pesées, **la plus ancienne d'abord**.
     *
     * Pas de plage, et c'est délibéré. Le calendrier borne ses lectures parce que le
     * journal alimentaire compte des dizaines de lignes par jour ; ici il y en a **au
     * plus une**, et dix ans de pesées quotidiennes tiennent en trois mille couples
     * date-poids. Une lecture bornée coûterait une méthode de port, un débordement de
     * six jours à gauche pour que la moyenne mobile du premier jour existe, et le
     * chargement séparé du poids de départ de l'objectif — pour n'économiser rien de
     * mesurable.
     *
     * L'ordre croissant est celui de la courbe, qui se lit de gauche à droite.
     *
     * Le nom dit **l'historique** et non « tout » : [Goals.observeAll][goals] existe
     * déjà, et l'adaptateur Room porte les deux ports.
     *
     * [goals]: app.hexavore.domain.goal.Goals.observeAll
     */
    fun observeHistory(): Flow<List<WeightEntry>>

    /** La dernière pesée connue, celle qui sert au calcul de l'objectif. */
    fun observeLatest(): Flow<WeightEntry?>

    suspend fun record(entry: WeightEntry)

    /**
     * Retire la pesée de [date], s'il y en a une ; sinon, ne fait rien.
     *
     * **Par la date, et non par un identifiant** : il n'y a qu'une pesée par jour, et
     * c'est la date que l'écran tient. Une pesée tapée sur le mauvais jour restait dans
     * la courbe, dans la pente de l'adaptation et — la plus récente — dans le poids qui
     * sert au calcul ([D165][decisions]) : la corriger demandait de tout effacer.
     *
     * [decisions]: docs/11-decisions.md
     */
    suspend fun delete(date: LocalDate)
}
