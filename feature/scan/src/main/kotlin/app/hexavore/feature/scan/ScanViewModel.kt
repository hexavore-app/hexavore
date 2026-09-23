package app.hexavore.feature.scan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.food.Barcode
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.ProductLookup
import app.hexavore.domain.usecase.LookupBarcode
import app.hexavore.domain.usecase.StageDishPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ce que l'écran de scan montre.
 *
 * La permission n'y figure pas : c'est un état de l'appareil, que la composable lit
 * et redemande. Le mettre ici obligerait le `ViewModel` à connaître un `Context`,
 * donc à cesser d'être vérifiable sur la JVM.
 */
@Immutable
internal sealed interface ScanUiState {
    /** L'aperçu tourne, rien n'a encore été lu. */
    data object Scanning : ScanUiState

    /**
     * Le code est lu, on cherche.
     *
     * Distinct de [Scanning] parce que l'attente doit se voir — mais **en surimpression
     * et sans dialogue** : [docs/02][parcours] veut un chargement inline.
     *
     * L'aperçu ne disparaît pas pour autant : il se **fige** sur la trame qui vient
     * d'être lue ([D69][decisions]). Figer n'est pas couper — l'image reste, elle
     * cesse seulement de bouger, et c'est elle qui dit *ce que* l'appareil a lu.
     *
     * [parcours]: docs/02-parcours-et-ecrans.md
     * [decisions]: docs/11-decisions.md
     */
    data object Looking : ScanUiState

    /** Trouvé. L'écran se referme sur la validation ; c'est la route qui l'emmène. */
    data class Found(val food: FoodId) : ScanUiState

    /** Open Food Facts a répondu et ne connaît pas ce code. */
    data class Unknown(val code: Barcode) : ScanUiState

    /**
     * On n'a pas pu demander.
     *
     * Séparé d'[Unknown] parce que les deux ne disent pas la même chose : l'un
     * affirme une absence, l'autre dit que la question reste posée. Les confondre
     * annoncerait une absence qu'on n'a pas vérifiée.
     */
    data class Unreachable(val code: Barcode) : ScanUiState
}

/**
 * Le scan, entre la caméra et le catalogue.
 *
 * Il ne connaît ni la caméra ni le réseau : il reçoit un [Barcode] déjà confirmé par
 * l'anti-rebond, et le passe à un cas d'usage qui décide seul de l'ordre — catalogue
 * d'abord, service ensuite ([D64][decisions]). C'est ce qui le rend vérifiable sur la
 * JVM, alors que tout ce qui l'entoure demande un appareil.
 *
 * [decisions]: docs/11-decisions.md
 */
@HiltViewModel
internal class ScanViewModel @Inject constructor(
    private val lookupBarcode: LookupBarcode,
    private val stagePhoto: StageDishPhoto,
) : ViewModel() {
    private val state = MutableStateFlow<ScanUiState>(ScanUiState.Scanning)
    val uiState: StateFlow<ScanUiState> = state.asStateFlow()

    /**
     * Ce qui rouvre la lecture de l'anti-rebond, et pourquoi c'est un compteur.
     *
     * Rescanner le **même** produit doit remarcher. Un booléen repassé à la même
     * valeur ne relancerait aucun effet, et l'écran resterait sourd jusqu'à ce qu'on
     * vise autre chose.
     */
    private val resumes = MutableStateFlow(0)
    val resumeKey: StateFlow<Int> = resumes.asStateFlow()

    /**
     * Un code confirmé par deux lectures.
     *
     * **Ignoré si une recherche est déjà en cours ou terminée.** L'anti-rebond se
     * tait déjà après une confirmation, mais l'écran ne peut pas en dépendre : les
     * deux gardes tiennent la même propriété à deux endroits, et celle-ci est celle
     * qui survit à un changement de décodeur.
     */
    fun onBarcode(code: Barcode) {
        if (state.value != ScanUiState.Scanning) return

        state.value = ScanUiState.Looking
        viewModelScope.launch {
            state.value = when (val found = runCatching { lookupBarcode(code) }.getOrNull()) {
                is ProductLookup.Found -> ScanUiState.Found(found.food.id)
                ProductLookup.Unknown -> ScanUiState.Unknown(code)
                // `null` couvre l'echec de lecture du catalogue, et il aboutit ici :
                // l'ecran propose alors de creer la fiche a la main, ce qui est le
                // geste utile dans les deux cas.
                else -> ScanUiState.Unreachable(code)
            }
        }
    }

    /**
     * La trame sur laquelle le code a été lu, en JPEG.
     *
     * **Des octets et non un `Bitmap`** : c'est ce qui garde ce modèle vérifiable sur
     * la JVM alors que la caméra ne l'est pas ([D66][decisions]). Le dépôt décide
     * ensuite s'il la garde, selon le réglage des photos ; cet écran ne le connaît pas.
     *
     * [decisions]: docs/11-decisions.md
     */
    fun onFrame(jpeg: ByteArray) {
        viewModelScope.launch { runCatching { stagePhoto(jpeg) } }
    }

    /** Après que l'écran a fini avec un code : on revise. */
    fun onResume() {
        state.value = ScanUiState.Scanning
        resumes.value += 1
    }
}
