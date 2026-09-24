package app.hexavore.di

import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.DishPhotos
import app.hexavore.domain.diary.PhotoSettings
import app.hexavore.domain.usecase.AttachDishPhoto
import app.hexavore.domain.usecase.ChoosePhotoKeeping
import app.hexavore.domain.usecase.ObserveDishPhotos
import app.hexavore.domain.usecase.ObservePhotoKeeping
import app.hexavore.domain.usecase.OpenDraftPhoto
import app.hexavore.domain.usecase.PurgeDishPhotos
import app.hexavore.domain.usecase.StageDishPhoto
import app.hexavore.domain.usecase.SweepDishPhotos
import app.hexavore.domain.usecase.WeighDishPhotos
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Construction des cas d'usage des **photos de plats**.
 *
 * Un module à part et non huit fonctions de plus dans [DomainModule], pour la raison
 * que ce dernier explique : le projet découpe selon ce que les choses sont plutôt que
 * de relever le seuil de detekt. Ces cas d'usage parlent d'un dossier et d'un réglage,
 * les autres de plats.
 *
 * @see docs/09-donnees-et-sauvegarde.md
 */
@Module
@InstallIn(SingletonComponent::class)
object PhotoUseCaseModule {
    @Provides
    fun stageDishPhoto(photos: DishPhotos, settings: PhotoSettings): StageDishPhoto = StageDishPhoto(photos, settings)

    @Provides
    fun openDraftPhoto(photos: DishPhotos): OpenDraftPhoto = OpenDraftPhoto(photos)

    @Provides
    fun attachDishPhoto(photos: DishPhotos): AttachDishPhoto = AttachDishPhoto(photos)

    @Provides
    fun observeDishPhotos(photos: DishPhotos): ObserveDishPhotos = ObserveDishPhotos(photos)

    @Provides
    fun sweepDishPhotos(photos: DishPhotos, diary: DiaryRepository): SweepDishPhotos = SweepDishPhotos(photos, diary)

    @Provides
    fun weighDishPhotos(photos: DishPhotos): WeighDishPhotos = WeighDishPhotos(photos)

    @Provides
    fun purgeDishPhotos(photos: DishPhotos): PurgeDishPhotos = PurgeDishPhotos(photos)

    @Provides
    fun observePhotoKeeping(settings: PhotoSettings): ObservePhotoKeeping = ObservePhotoKeeping(settings)

    @Provides
    fun choosePhotoKeeping(settings: PhotoSettings, photos: DishPhotos): ChoosePhotoKeeping =
        ChoosePhotoKeeping(settings, photos)
}
