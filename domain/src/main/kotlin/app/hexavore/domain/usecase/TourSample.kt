package app.hexavore.domain.usecase

import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.EntryId
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.FoodEntry
import app.hexavore.domain.nutrition.Macros
import app.hexavore.domain.time.Clock

/**
 * La journée que le tour guidé montre, et qui n'a jamais eu lieu.
 *
 * ### Pourquoi de fausses données plutôt qu'un écran vide
 *
 * Un tour qui désigne un hexagone sans aires et six compteurs à zéro explique des
 * dessins vides. Ce qu'on veut montrer — *voici ce que devient une journée notée* — a
 * besoin d'une journée notée ([D141][decisions]).
 *
 * ### Écrites, puis effacées
 *
 * Elles passent par le **vrai dépôt**, et c'est délibéré : l'hexagone, les compteurs,
 * la liste et l'anneau de niveau lisent tous le journal, et un jeu de données posé à
 * côté aurait demandé de doubler ce chemin-là dans chacun d'eux. Le tour montre donc
 * l'application en train de fonctionner, pas une maquette qui lui ressemble.
 *
 * Le prix est qu'il faut les reprendre, et [HideTourSample] le fait à la fin du tour —
 * y compris quand on l'abandonne en cours de route. Les identifiants sont **fixes** :
 * c'est ce qui permet de les retrouver au lancement suivant si l'application a été
 * fermée pendant le tour, plutôt que de laisser trois plats fantômes dans le journal de
 * quelqu'un.
 *
 * [decisions]: docs/11-decisions.md
 */
class ShowTourSample(private val diary: DiaryRepository, private val clock: Clock) {
    suspend operator fun invoke() {
        val today = clock.today()
        val now = clock.now()

        TOUR_DISHES.forEach { modele ->
            val id = DishId(modele.id)
            diary.save(
                Dish(
                    id = id,
                    date = today,
                    source = modele.source,
                    // Decale d'une minute par plat : la liste s'affiche dans l'ordre ou
                    // les plats ont ete notes, et trois plats au meme instant
                    // s'ordonneraient au hasard des lectures.
                    loggedAt = now.plusSeconds(modele.rank * SECONDS_APART),
                    title = modele.title,
                    entries = modele.lines.mapIndexed { index, ligne ->
                        FoodEntry(
                            id = EntryId("${modele.id}-$index"),
                            dishId = id,
                            displayName = ligne.name,
                            quantity = ligne.grams,
                            unit = "g",
                            grams = ligne.grams,
                            macros = ligne.macros,
                        )
                    },
                ),
            )
        }
    }
}

/**
 * Reprend ce que [ShowTourSample] a posé.
 *
 * **Appelée à la fin du tour et au lancement suivant**, parce qu'une application fermée
 * au milieu d'un tour laisserait sinon ces plats dans un vrai journal. Supprimer ce qui
 * n'est pas là ne coûte rien, et c'est ce qui rend l'appel sûr à répéter.
 */
class HideTourSample(private val diary: DiaryRepository) {
    suspend operator fun invoke() {
        TOUR_DISHES.forEach { diary.deleteDish(DishId(it.id)) }
    }
}

/** Un plat du tour, et ce qu'il montre. */
private class TourDish(
    val id: String,
    val rank: Long,
    val title: String,
    val source: EntrySource,
    val lines: List<TourLine>,
)

private class TourLine(val name: String, val grams: Double, val macros: Macros)

/**
 * Trois plats, trois sources.
 *
 * **Une par geste que le tour présente** : une photo, une description, une recherche.
 * La liste de l'accueil montre ainsi les trois pastilles de provenance, et la phrase du
 * tour désigne quelque chose que l'œil trouve.
 *
 * Les chiffres sont plausibles sans prétendre à l'exactitude : ils remplissent un
 * hexagone, ils ne nourrissent personne. Les libellés ne sont pas traduits — ce sont des
 * noms d'aliments, et le catalogue ne les traduit pas davantage.
 */
private val TOUR_DISHES = listOf(
    TourDish(
        id = "tour-1",
        rank = 0,
        title = "Porridge",
        source = EntrySource.TEXT_AI,
        lines = listOf(
            TourLine(
                "Flocons d'avoine",
                60.0,
                Macros(kcal = 225.0, protein = 8.0, carbs = 36.0, sugars = 1.0, fat = 4.0, fiber = 6.0),
            ),
            TourLine(
                "Lait demi-écrémé",
                200.0,
                Macros(kcal = 94.0, protein = 7.0, carbs = 10.0, sugars = 10.0, fat = 3.0, fiber = 0.0),
            ),
        ),
    ),
    TourDish(
        id = "tour-2",
        rank = 1,
        title = "Poke bowl",
        source = EntrySource.PHOTO_AI,
        lines = listOf(
            TourLine(
                "Riz blanc cuit",
                180.0,
                Macros(kcal = 234.0, protein = 4.0, carbs = 52.0, sugars = 0.0, fat = 1.0, fiber = 1.0),
            ),
            TourLine(
                "Saumon cru",
                90.0,
                Macros(kcal = 185.0, protein = 18.0, carbs = 0.0, sugars = 0.0, fat = 12.0, fiber = 0.0),
            ),
            TourLine(
                "Avocat",
                70.0,
                Macros(kcal = 120.0, protein = 1.0, carbs = 2.0, sugars = 0.0, fat = 11.0, fiber = 5.0),
            ),
        ),
    ),
    TourDish(
        id = "tour-3",
        rank = 2,
        title = "Pomme",
        source = EntrySource.MANUAL,
        lines = listOf(
            TourLine(
                "Pomme",
                150.0,
                Macros(kcal = 78.0, protein = 0.0, carbs = 19.0, sugars = 15.0, fat = 0.0, fiber = 3.0),
            ),
        ),
    ),
)

/** Une minute entre deux plats : assez pour les ordonner, trop peu pour se remarquer. */
private const val SECONDS_APART = 60L
