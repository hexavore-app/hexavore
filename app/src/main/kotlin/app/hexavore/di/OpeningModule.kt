package app.hexavore.di

import app.hexavore.domain.ai.AiSettings
import app.hexavore.domain.opening.OpeningContext
import app.hexavore.domain.opening.OpeningLedger
import app.hexavore.domain.opening.OpeningSituations
import app.hexavore.domain.opening.TipSettings
import app.hexavore.domain.report.CrashReports
import app.hexavore.domain.time.Clock
import app.hexavore.domain.tour.TourSettings
import app.hexavore.domain.usecase.ObserveProgress
import app.hexavore.domain.usecase.OpeningMessages
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Ce que l'accueil dit en s'ouvrant (D179), construit une fois.
 *
 * Un module a part, comme celui des pastilles et pour la meme raison : le choix d'un
 * message lit quatre sources qui n'ont rien a voir entre elles -- une cle, le tour, un
 * plantage, la progression -- et les poser ailleurs aurait fait dependre un module de
 * sujets qui ne le concernent pas.
 */
@Module
@InstallIn(SingletonComponent::class)
object OpeningModule {
    @Provides
    fun openingContext(
        ai: AiSettings,
        tour: TourSettings,
        crashes: CrashReports,
        progress: ObserveProgress,
    ): OpeningContext = OpeningSituations(ai, tour, crashes, progress)

    @Provides
    fun openingMessages(
        ledger: OpeningLedger,
        tips: TipSettings,
        context: OpeningContext,
        clock: Clock,
    ): OpeningMessages = OpeningMessages(ledger, tips, context, clock)
}
