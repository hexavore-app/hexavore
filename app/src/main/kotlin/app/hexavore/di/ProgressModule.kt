package app.hexavore.di

import app.hexavore.data.progress.RoomProgressStore
import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.goal.Goals
import app.hexavore.domain.progress.ProgressStore
import app.hexavore.domain.time.Clock
import app.hexavore.domain.usecase.AdvanceProgress
import app.hexavore.domain.usecase.ObserveProgress
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * La progression : un port, un adaptateur, et les deux cas d'usage qui l'encadrent.
 *
 * **Lire et faire avancer sont séparés**, et le graphe le montre : [ObserveProgress]
 * ne voit jamais d'écriture, [AdvanceProgress] ne voit jamais le journal. Un seul cas
 * d'usage qui ferait les deux se déclencherait lui-même — l'écriture réémet le dépôt,
 * qui relance le calcul, qui réécrit ([D132][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Module
@InstallIn(SingletonComponent::class)
object ProgressModule {
    @Provides
    fun progressStore(store: RoomProgressStore): ProgressStore = store

    @Provides
    fun observeProgress(diary: DiaryRepository, goals: Goals, store: ProgressStore, clock: Clock): ObserveProgress =
        ObserveProgress(diary, goals, store, clock)

    @Provides
    fun advanceProgress(store: ProgressStore, clock: Clock): AdvanceProgress = AdvanceProgress(store, clock)
}
