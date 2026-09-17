package app.hexavore.di

import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.FavoriteDishes
import app.hexavore.domain.diary.SelectedDay
import app.hexavore.domain.food.FoodUsage
import app.hexavore.domain.goal.Goals
import app.hexavore.domain.identity.IdGenerator
import app.hexavore.domain.time.Clock
import app.hexavore.domain.usecase.CreateDraft
import app.hexavore.domain.usecase.DeleteDish
import app.hexavore.domain.usecase.GetCalendar
import app.hexavore.domain.usecase.GetDaySummary
import app.hexavore.domain.usecase.GetDishDraft
import app.hexavore.domain.usecase.LogDish
import app.hexavore.domain.usecase.RestoreDish
import app.hexavore.domain.usecase.SaveDraft
import app.hexavore.domain.usecase.UpdateDish
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Construction des cas d'usage du **journal**.
 *
 * Ils vivent dans `:domain`, qui est du Kotlin pur et ne connaît donc pas Hilt —
 * c'est exactement la propriété qu'on veut. `:app` assemble : il sait quels ports
 * sont liés à quoi, et il monte les cas d'usage par-dessus.
 *
 * Un cas d'usage n'est pas un singleton : il ne porte aucun état, et l'instancier
 * coûte moins que de le retenir.
 *
 * **Séparé de [GoalUseCaseModule] puis de [FoodUseCaseModule]**, chaque fois parce
 * qu'un seul module atteignait le seuil de fonctions de detekt. La réponse du projet
 * est de découper selon ce que les choses sont, pas de relever le seuil
 * ([docs/10][qualite]) — et les deux fois, la coupure existait déjà dans la lecture :
 * ces cas d'usage parlent de plats, les autres d'objectifs et de fiches.
 *
 * [qualite]: docs/10-qualite-et-livraison.md
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    fun getDaySummary(diary: DiaryRepository, goals: Goals, clock: Clock): GetDaySummary =
        GetDaySummary(diary, goals, clock)

    /**
     * Le calendrier, qui lit une plage la ou [GetDaySummary] lit un jour.
     *
     * Pas d'horloge ici : les bornes viennent de l'appelant, qui sait quel mois il
     * affiche. Lui en donner une l'inviterait a decider tout seul de la plage.
     */
    @Provides
    fun getCalendar(diary: DiaryRepository, goals: Goals): GetCalendar = GetCalendar(diary, goals)

    @Provides
    fun getDishDraft(diary: DiaryRepository, ids: IdGenerator): GetDishDraft = GetDishDraft(diary, ids)

    @Provides
    fun createDraft(clock: Clock, ids: IdGenerator, selected: SelectedDay): CreateDraft =
        CreateDraft(clock, ids, selected)

    @Provides
    fun logDish(
        diary: DiaryRepository,
        foods: FoodUsage,
        favorites: FavoriteDishes,
        clock: Clock,
        ids: IdGenerator,
    ): LogDish = LogDish(diary, foods, favorites, clock, ids)

    @Provides
    fun updateDish(diary: DiaryRepository, ids: IdGenerator): UpdateDish = UpdateDish(diary, ids)

    @Provides
    fun saveDraft(log: LogDish, update: UpdateDish): SaveDraft = SaveDraft(log, update)

    @Provides
    fun deleteDish(diary: DiaryRepository): DeleteDish = DeleteDish(diary)

    @Provides
    fun restoreDish(diary: DiaryRepository): RestoreDish = RestoreDish(diary)
}
