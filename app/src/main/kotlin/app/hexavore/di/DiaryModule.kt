package app.hexavore.di

import app.hexavore.BuildConfig
import app.hexavore.data.diary.DishPhotoFiles
import app.hexavore.data.diary.RoomDiaryRepository
import app.hexavore.data.diary.RoomFavoriteDishes
import app.hexavore.data.diary.RoomFoodCitations
import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.DishPhotos
import app.hexavore.domain.diary.FavoriteDishes
import app.hexavore.domain.diary.PhotoBytes
import app.hexavore.domain.food.FoodCitations
import app.hexavore.domain.time.Clock
import app.hexavore.domain.usecase.HideTourSample
import app.hexavore.domain.usecase.ShowTourSample
import app.hexavore.feature.home.tour.TourReplay
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Le journal, désormais lu depuis Room.
 *
 * **C'était le critère de fin de la tranche 1**, et il est tenu : passer de
 * l'implémentation en mémoire à Room n'a changé que le corps de [diaryRepository].
 * Aucun cas d'usage, aucun ViewModel, aucun écran n'a bougé — ils ne connaissent
 * que le port.
 *
 * Le jeu de démonstration a disparu avec la bascule. L'application démarre donc sur
 * une journée vide, ce qui est le comportement exact tant que la tranche 2 n'a pas
 * apporté la saisie : afficher des plats que l'utilisateur n'a pas notés serait
 * mentir sur l'état de son journal.
 *
 * @see docs/12-plan-de-developpement.md
 */
@Module
@InstallIn(SingletonComponent::class)
object DiaryModule {
    @Provides
    @Singleton
    fun diaryRepository(implementation: RoomDiaryRepository): DiaryRepository = implementation

    /**
     * Les plats favoris, second port de ce module.
     *
     * Séparé de [DiaryRepository] parce qu'un favori n'est pas une entrée de journal :
     * l'un est un modèle réutilisable qui suit les fiches vivantes, l'autre un registre
     * d'événements qui fige ses valeurs ([D62][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    @Provides
    fun favoriteDishes(store: RoomFavoriteDishes): FavoriteDishes = store

    /**
     * Le compte des citations, fourni ici alors que c'est un port du **catalogue**.
     *
     * Ce n'est pas un rangement approximatif : le compte se dérive des entrées de
     * journal, et son adaptateur vit dans ce module pour que le contrat du journal
     * puisse l'éprouver — écrire un plat, puis constater que le compte a bougé. Il a
     * vécu sur le catalogue, où le faux ne pouvait l'exposer qu'en carte posée à la
     * main ([D71][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    @Provides
    fun foodCitations(citations: RoomFoodCitations): FoodCitations = citations

    /**
     * Les photos des plats, troisième port de ce module.
     *
     * **Des fichiers et non des lignes**, dans le module qui range pourtant du Room :
     * une photo est l'accessoire d'un plat, elle porte son identifiant pour nom, et la
     * loger ailleurs aurait inventé un `:data:photo` dont le seul contenu serait un
     * dossier.
     */
    @Provides
    fun dishPhotos(files: DishPhotoFiles): DishPhotos = files

    /**
     * Les octets d'une photo, pour ce qui doit la transporter.
     *
     * **Le même adaptateur**, un second port : la séparation existe pour que les
     * appelants ne dépendent que de ce qu'ils utilisent ([D138][decisions]) — le
     * signalement lit des octets et ne range rien.
     *
     * [decisions]: docs/11-decisions.md
     */
    @Provides
    fun photoBytes(files: DishPhotoFiles): PhotoBytes = files

    /**
     * Les plats d'exemple du tour guidé, posés puis repris.
     *
     * Ils passent par le **vrai dépôt** : l'hexagone, les compteurs et la liste lisent
     * tous le journal, et un jeu de données posé à côté aurait demandé de doubler ce
     * chemin dans chacun d'eux ([D141][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    @Provides
    fun showTourSample(diary: DiaryRepository, clock: Clock): ShowTourSample = ShowTourSample(diary, clock)

    @Provides
    fun hideTourSample(diary: DiaryRepository): HideTourSample = HideTourSample(diary)

    /**
     * Le tour se rejoue-t-il a chaque lancement ?
     *
     * **La variante publiee ne peut pas repondre oui**, et c'est tout l'interet d'avoir
     * sorti ce reglage du tour : il ne depend plus de quelqu'un pour penser a le
     * remettre a sa place avant de livrer. Le dial ci-dessous ne vaut que dans `debug`,
     * la ou l'on regarde le tour vingt fois de suite sans effacer ses donnees entre
     * deux essais.
     *
     * Le souvenir, lui, continue de s'ecrire dans les deux variantes : c'est lui qui
     * decidera seul le jour ou [REJOUE_LE_TOUR] repassera a `false`.
     */
    @Provides
    fun tourReplay(): TourReplay = if (BuildConfig.DEBUG) TourReplay(REJOUE_LE_TOUR) else TourReplay.NEVER
}

/**
 * Le dial d'essai, **sans effet hors de la variante `debug`**.
 *
 * A remettre a `false` quand le tour n'a plus besoin d'etre regarde en boucle. L'oublier
 * ne coute plus rien a personne d'autre qu'a celui qui developpe.
 */
private const val REJOUE_LE_TOUR = true
