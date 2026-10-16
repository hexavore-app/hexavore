package app.hexavore.feature.weight

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import java.time.LocalDate

/**
 * La liste des pesées remonte-t-elle en tête ? ([D169][decisions])
 *
 * **Oui quand une pesée plus récente que la première ligne vient d'y entrer, et que la
 * liste était lue depuis son haut.** Une liste paresseuse garde sa première ligne visible
 * par sa clé : la pesée insérée au-dessus — celle qu'« Annuler » vient de rendre, ou celle
 * qu'on vient de noter pour aujourd'hui — restait au-dessus du bord, hors de vue, et
 * l'annulation semblait n'avoir rien fait.
 *
 * **Pas quand on a fait défiler** : qui relit septembre n'a pas à être ramené en octobre
 * parce qu'une pesée est arrivée. **Pas quand la tête n'a pas changé** : une pesée plus
 * ancienne s'insère plus bas, à sa place, et une valeur corrigée ne déplace rien.
 *
 * [decisions]: docs/11-decisions.md
 */
internal fun keepsNewestInView(shownNewest: LocalDate?, newest: LocalDate?, atTop: Boolean): Boolean =
    atTop && shownNewest != null && newest != null && newest > shownNewest

/**
 * L'état de la liste des pesées, qui garde en vue la plus récente quand elle arrive
 * ([keepsNewestInView]).
 *
 * **La demande précède la mesure qui insère la ligne.** `requestScrollToItem` ne vaut que
 * pour la mesure suivante, où elle remplace l'ancrage sur la clé de la première ligne
 * visible. L'effet s'exécute à l'application de la composition, avant la mesure de la
 * même image : la position qu'il lit est encore celle d'avant l'insertion.
 *
 * Rien à la première composition, ni à celle qui suit une rotation : la liste y garde
 * ce qu'on y regardait.
 */
@Composable
internal fun rememberJournalListState(newest: LocalDate?): LazyListState {
    val listState = rememberLazyListState()
    val shown = remember { mutableStateOf(newest) }

    SideEffect {
        val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        if (keepsNewestInView(shown.value, newest, atTop)) listState.requestScrollToItem(0)
        shown.value = newest
    }
    return listState
}
