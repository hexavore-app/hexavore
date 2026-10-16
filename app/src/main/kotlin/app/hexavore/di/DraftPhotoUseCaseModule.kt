package app.hexavore.di

import app.hexavore.domain.diary.DishPhotos
import app.hexavore.domain.diary.PhotoSettings
import app.hexavore.domain.usecase.AttachDishPhoto
import app.hexavore.domain.usecase.ChooseDishPhoto
import app.hexavore.domain.usecase.KeptDraftPhoto
import app.hexavore.domain.usecase.OpenDraftPhoto
import app.hexavore.domain.usecase.StageDishPhoto
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Construction des cas d'usage de **l'image d'un brouillon** : la déposer, l'ouvrir, la
 * choisir, la rendre après la mort du processus, puis la ranger avec le plat.
 *
 * Séparés de [PhotoUseCaseModule] quand le cinquième est arrivé (D166) : ceux-ci suivent
 * un brouillon d'écran en écran, ceux-là gèrent un dossier et un réglage. Le seuil de
 * detekt a mordu, et la réponse est de découper selon ce que les choses sont.
 */
@Module
@InstallIn(SingletonComponent::class)
object DraftPhotoUseCaseModule {
    @Provides
    fun stageDishPhoto(photos: DishPhotos, settings: PhotoSettings): StageDishPhoto = StageDishPhoto(photos, settings)

    @Provides
    fun openDraftPhoto(photos: DishPhotos): OpenDraftPhoto = OpenDraftPhoto(photos)

    @Provides
    fun attachDishPhoto(photos: DishPhotos): AttachDishPhoto = AttachDishPhoto(photos)

    // Sans `PhotoSettings`, et c'est tout le sujet : un geste explicite l'emporte sur
    // un reglage qui ne parle que de ce qu'on garde **automatiquement** (D156).
    @Provides
    fun chooseDishPhoto(photos: DishPhotos): ChooseDishPhoto = ChooseDishPhoto(photos)

    /** L'image d'un brouillon rendu apres la mort du processus, si elle est encore la (D166). */
    @Provides
    fun keptDraftPhoto(photos: DishPhotos): KeptDraftPhoto = KeptDraftPhoto(photos)
}
