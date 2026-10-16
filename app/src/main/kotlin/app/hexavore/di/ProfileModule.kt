package app.hexavore.di

import app.hexavore.data.profile.RoomProfileStore
import app.hexavore.domain.goal.Goals
import app.hexavore.domain.profile.Profiles
import app.hexavore.domain.profile.WeightLog
import app.hexavore.domain.usecase.ChooseUnitSystem
import app.hexavore.domain.usecase.ObserveHeight
import app.hexavore.domain.usecase.ObserveUnitSystem
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Le profil, le poids et les objectifs : trois ports, un adaptateur.
 *
 * Même forme que [FoodModule], et pour la même raison : la séparation paie du côté
 * des **appelants**. L'accueil ne voit que [Goals] ; l'onboarding écrit les trois.
 *
 * @see docs/06-architecture.md
 */
@Module
@InstallIn(SingletonComponent::class)
object ProfileModule {
    @Provides
    fun profiles(store: RoomProfileStore): Profiles = store

    @Provides
    fun weightLog(store: RoomProfileStore): WeightLog = store

    @Provides
    fun goals(store: RoomProfileStore): Goals = store

    /**
     * Le systeme d unites, lu et choisi.
     *
     * Ici et non dans un module d apparence : le reglage est une colonne du profil, et
     * c est ce qui le fait voyager dans la sauvegarde -- contrairement au theme, qui
     * est une preference d appareil.
     */
    @Provides
    fun observeUnitSystem(profiles: Profiles): ObserveUnitSystem = ObserveUnitSystem(profiles)

    @Provides
    fun chooseUnitSystem(profiles: Profiles): ChooseUnitSystem = ChooseUnitSystem(profiles)

    /** La taille, sur laquelle le journal juge une pesee avant de l ecrire (D160). */
    @Provides
    fun observeHeight(profiles: Profiles): ObserveHeight = ObserveHeight(profiles)
}
