package app.hexavore.di

import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.goal.AdjustmentSettings
import app.hexavore.domain.goal.Goals
import app.hexavore.domain.identity.IdGenerator
import app.hexavore.domain.profile.Profiles
import app.hexavore.domain.profile.WeightLog
import app.hexavore.domain.time.Clock
import app.hexavore.domain.usecase.CalculateDailyGoal
import app.hexavore.domain.usecase.GetWeightTrend
import app.hexavore.domain.usecase.RecordWeight
import app.hexavore.domain.usecase.RespondToAdjustment
import app.hexavore.domain.usecase.ReviseGoal
import app.hexavore.domain.usecase.SuggestGoalAdjustment
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Construction des cas d'usage de l'**objectif**.
 *
 * Même raisonnement que [DomainModule], dont il a été séparé : les cas d'usage du
 * journal et ceux de l'objectif ne se lisent pas ensemble et ne changent pas pour les
 * mêmes raisons. La tranche 7 y a ajouté le journal de poids, qui appartient au
 * même sujet : la courbe se lit contre la trajectoire annoncée par l'objectif.
 */
@Module
@InstallIn(SingletonComponent::class)
object GoalUseCaseModule {
    @Provides
    fun calculateDailyGoal(clock: Clock): CalculateDailyGoal = CalculateDailyGoal(clock)

    @Provides
    fun getWeightTrend(weights: WeightLog, goals: Goals): GetWeightTrend = GetWeightTrend(weights, goals)

    /** La taille du profil juge la pesee avant qu'elle s'ecrive (D160). */
    @Provides
    fun recordWeight(weights: WeightLog, profiles: Profiles, clock: Clock): RecordWeight =
        RecordWeight(weights, profiles, clock)

    /**
     * L'adaptation hebdomadaire.
     *
     * Cinq sources, et chacune répond à une des conditions : les pesées donnent la
     * pente, le journal l'adhérence, l'objectif le cap annoncé, le profil la dépense
     * dont les garde-fous ont besoin, et les réglages le silence après une réponse.
     */
    @Suppress("LongParameterList")
    @Provides
    fun suggestGoalAdjustment(
        weights: WeightLog,
        diary: DiaryRepository,
        goals: Goals,
        profiles: Profiles,
        settings: AdjustmentSettings,
        clock: Clock,
    ): SuggestGoalAdjustment = SuggestGoalAdjustment(weights, diary, goals, profiles, settings, clock)

    @Provides
    fun respondToAdjustment(
        goals: Goals,
        settings: AdjustmentSettings,
        ids: IdGenerator,
        clock: Clock,
    ): RespondToAdjustment = RespondToAdjustment(goals, settings, ids, clock)

    @Provides
    fun reviseGoal(profiles: Profiles, weights: WeightLog, goals: Goals, clock: Clock, ids: IdGenerator): ReviseGoal =
        ReviseGoal(profiles, weights, goals, clock, ids)
}
