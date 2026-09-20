package app.hexavore.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.R
import app.hexavore.domain.diary.DishTitle
import app.hexavore.domain.diary.MealMoment

/**
 * Les mots d'un titre de plat.
 *
 * **Ici et non dans chaque écran**, parce que trois surfaces les demandent — la liste
 * des plats, l'écran de validation, et la boîte qui propose un nom de favori — et que
 * trois copies auraient fini par dire « Goûter » à deux endroits et « Collation » au
 * troisième. C'est la règle du design system appliquée à des mots plutôt qu'à une
 * géométrie : ce qui est partagé se pose une fois.
 *
 * Le domaine, lui, n'écrit pas de mots : il rend un [MealMoment] et un rang.
 */
@Composable
fun momentLabel(moment: MealMoment): String = stringResource(moment.labelRes)

/**
 * Le titre complet d'un plat, rang compris.
 *
 * « Déjeuner », puis « Déjeuner 2 » pour le dessert noté à part. Le rang ne s'écrit
 * qu'à partir du deuxième : « Déjeuner 1 » laisserait croire qu'il en existe un autre,
 * alors que c'est précisément ce qu'on ne sait pas encore au moment de l'écrire.
 */
@Composable
fun dishTitleText(title: DishTitle): String = when (title) {
    is DishTitle.Named -> title.text
    is DishTitle.Moment -> when {
        title.rank <= 1 -> momentLabel(title.moment)
        else -> stringResource(R.string.ds_dish_title_ranked, momentLabel(title.moment), title.rank)
    }
}

private val MealMoment.labelRes: Int
    @StringRes get() = when (this) {
        MealMoment.BREAKFAST -> R.string.ds_moment_breakfast
        MealMoment.LUNCH -> R.string.ds_moment_lunch
        MealMoment.SNACK -> R.string.ds_moment_snack
        MealMoment.DINNER -> R.string.ds_moment_dinner
    }
