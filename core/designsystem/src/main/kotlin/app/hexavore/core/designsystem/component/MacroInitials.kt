package app.hexavore.core.designsystem.component

import androidx.annotation.StringRes
import app.hexavore.core.designsystem.R
import app.hexavore.domain.nutrition.Macro

/**
 * L'initiale d'une macro, dans la langue de l'application ([D162][decisions]).
 *
 * **Une seule pour toute l'application** : l'hexagone et les pastilles des plats lisent
 * la même. L'hexagone écrivait les siennes en dur, au motif que ce seraient des
 * symboles de figure, les mêmes dans les deux langues. Ils ne l'étaient pas : « G » et
 * « L » ne disent rien en anglais, et son « F » désignait les fibres pendant que celui
 * des plats, juste dessous, désignait les lipides.
 *
 * [decisions]: docs/11-decisions.md
 */
val Macro.initialRes: Int
    @StringRes get() = when (this) {
        Macro.CALORIES -> R.string.ds_macro_initial_calories
        Macro.PROTEIN -> R.string.ds_macro_initial_protein
        Macro.CARBS -> R.string.ds_macro_initial_carbs
        Macro.SUGARS -> R.string.ds_macro_initial_sugars
        Macro.FAT -> R.string.ds_macro_initial_fat
        Macro.FIBER -> R.string.ds_macro_initial_fiber
    }
