package app.hexavore.core.testing

import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.profile.WeightLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Le journal de poids, en mémoire.
 *
 * Première implémentation du port, comme [InMemoryGoals] et [InMemoryProfiles].
 *
 * **Il tient un vrai journal, et non la dernière pesée écrite.** La version d'origine
 * gardait une seule entrée, ce qui lui faisait tenir deux propriétés du port par
 * accident : le tri n'existait pas, et [observeLatest]
 * rendait la dernière **écrite** là où Room rend celle du jour le plus **récent** —
 * donc rattraper une pesée oubliée la veille aurait fait recalculer l'objectif sur un
 * poids périmé. Aucun test ne le disait, parce que le seul appelant n'écrivait jamais
 * qu'une pesée. C'est la forme exacte du défaut que [D53][decisions] a nommée : le
 * faux plus indulgent que le vrai.
 *
 * [decisions]: docs/11-decisions.md
 */
class InMemoryWeightLog(initial: List<WeightEntry> = emptyList()) : WeightLog {
    private val state = MutableStateFlow(initial.recentesDAbord())

    /** Le journal entier, les plus récentes d'abord, pour qu'un test l'affirme sans flux. */
    val entries: List<WeightEntry> get() = state.value

    /** La pesée du jour le plus récent. */
    val latest: WeightEntry? get() = state.value.firstOrNull()

    /** Croissant, comme Room : c'est l'ordre de la courbe. */
    override fun observeHistory(): Flow<List<WeightEntry>> = state.map { it.reversed() }

    override fun observeLatest(): Flow<WeightEntry?> = state.map { it.firstOrNull() }

    /** Une pesée par jour : celle du jour est retirée avant que la neuve entre. */
    override suspend fun record(entry: WeightEntry) {
        state.value = (state.value.filterNot { it.date == entry.date } + entry).recentesDAbord()
    }

    override suspend fun delete(date: LocalDate) {
        state.value = state.value.filterNot { it.date == date }
    }
}

private fun List<WeightEntry>.recentesDAbord(): List<WeightEntry> = sortedByDescending { it.date }
