package app.hexavore.domain.usecase

import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.EntryDraft
import app.hexavore.domain.diary.FavoriteDishes
import app.hexavore.domain.diary.toEntries
import app.hexavore.domain.food.FoodUsage
import app.hexavore.domain.identity.IdGenerator
import app.hexavore.domain.time.Clock

/**
 * Enregistre un brouillon comme un **nouveau** plat.
 *
 * L'heure vient de l'horloge injectée et non du brouillon : c'est elle qui situe le
 * plat dans la journée, et un écran resté ouvert vingt minutes ne doit pas
 * l'enregistrer à l'heure où il a été ouvert.
 *
 * La journée, elle, vient du brouillon. Les deux ne se déduisent pas l'une de
 * l'autre : on note à 0 h 30 un dîner qui appartient à la veille.
 *
 * **C'est ici que les fiches citées sont marquées comme utilisées**, et pas au
 * moment où l'utilisateur les choisit dans la recherche. Marquer au tap aurait été
 * plus simple et ferait remonter dans « Récents » un aliment qu'on a regardé puis
 * abandonné : cette liste dit ce qu'on mange, pas ce qu'on a consulté.
 *
 * @see docs/06-architecture.md
 */
class LogDish(
    private val diary: DiaryRepository,
    private val foodUsage: FoodUsage,
    private val favorites: FavoriteDishes,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    /**
     * @throws IllegalArgumentException si le brouillon n'est pas complet. L'écran
     *   empêche le cas ; la vérification est là pour qu'un futur appelant ne
     *   puisse pas écrire une ligne à moitié saisie sans s'en apercevoir.
     */
    suspend operator fun invoke(draft: EntryDraft): DishId {
        require(draft.saveable) { "Brouillon incomplet : chaque ligne demande un nom, une quantite et une energie." }

        val id = DishId(ids.next())
        val now = clock.now()

        // Avant l'ecriture du plat, et pas apres : les lignes vont designer ces
        // fiches, et une entree qui pointe vers une fiche absente n'existe pas -- la
        // base la refuse. L'ordre inverse aurait paru plus prudent et n'aurait
        // jamais fonctionne.
        //
        // Ce que l'ecriture rend est la correspondance entre l'identifiant qu'une
        // ligne portait et celui sous lequel la fiche est rangee : les deux different
        // des qu'un aliment de l'ANSES etait deja au catalogue.
        val placed = foodUsage.remember(draft.foods, now)

        // Un favori rejoue remonte dans la liste, au meme endroit et pour la meme
        // raison que les fiches : cette liste dit ce qu'on mange, pas ce qu'on a
        // consulte. Marquer au choix du favori la ferait remonter sur un plat
        // finalement abandonne.
        draft.favoriteId?.let { favorites.markUsed(it) }

        diary.save(
            Dish(
                id = id,
                date = draft.date,
                source = draft.source,
                loggedAt = now,
                entries = draft.toEntries(id, ids, placed),
                favoriteId = draft.favoriteId,
                // Le titre n'est ecrit que s'il a ete ecrit : un plat sans titre
                // s'appelle du nom de son moment, et le figer en mots ici rendrait
                // l'application impossible a traduire pour rien.
                title = draft.title?.trim()?.takeIf { it.isNotEmpty() },
                moment = draft.moment,
            ),
        )
        return id
    }
}
