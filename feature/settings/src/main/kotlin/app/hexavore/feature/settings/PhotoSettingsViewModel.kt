package app.hexavore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.diary.PhotoWeight
import app.hexavore.domain.usecase.ChoosePhotoKeeping
import app.hexavore.domain.usecase.ObservePhotoKeeping
import app.hexavore.domain.usecase.PurgeDishPhotos
import app.hexavore.domain.usecase.WeighDishPhotos
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ce que l'écran des photos montre : le réglage, et ce que les fichiers pèsent. */
internal data class PhotoSettingsUiState(val keeping: Boolean = true, val weight: PhotoWeight = PhotoWeight.NOTHING)

/**
 * Garder les photos, et les effacer.
 *
 * **Le poids est relu, jamais observé.** Un dossier ne notifie pas, et l'entourer d'une
 * surveillance pour un chiffre qu'on regarde deux fois dans une vie coûterait plus que
 * ce qu'il rapporte. Il est donc relu à l'ouverture et après chaque effacement, ce qui
 * couvre les deux moments où il change sous les yeux de quelqu'un.
 *
 * **Éteindre n'efface rien**, et effacer n'éteint rien : ce sont deux gestes, et les
 * confondre ferait disparaître des mois d'images à qui voulait seulement cesser d'en
 * accumuler.
 */
@HiltViewModel
internal class PhotoSettingsViewModel @Inject constructor(
    observeKeeping: ObservePhotoKeeping,
    private val chooseKeeping: ChoosePhotoKeeping,
    private val weigh: WeighDishPhotos,
    private val purge: PurgeDishPhotos,
) : ViewModel() {
    private val weight = MutableStateFlow(PhotoWeight.NOTHING)

    val uiState: StateFlow<PhotoSettingsUiState> = observeKeeping()
        // Un fichier de preferences illisible retombe sur le defaut plutot que sur un
        // ecran vide : c'est la regle du projet, et rien ici ne justifie de refuser
        // l'ecran a quelqu'un dont un fichier a ete abime.
        .catch { emit(true) }
        .combine(weight) { keeping, weight -> PhotoSettingsUiState(keeping, weight) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = PhotoSettingsUiState(),
        )

    init {
        refresh()
    }

    fun onKeeping(keep: Boolean) {
        viewModelScope.launch { runCatching { chooseKeeping(keep) } }
    }

    /**
     * L'effacement, une fois la confirmation obtenue.
     *
     * Le poids est relu **après**, et non remis à zéro à la main : un effacement qui
     * échoue à moitié doit se voir, et le seul moyen de le savoir est de recompter.
     */
    fun onPurge() {
        viewModelScope.launch {
            runCatching { purge() }
            refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch { weight.value = weigh() }
    }
}

/** Assez pour traverser une rotation sans relire, trop peu pour ecouter un ecran quitte. */
private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
