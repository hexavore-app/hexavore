package app.hexavore.feature.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.report.CrashReports
import app.hexavore.domain.usecase.ReportCrash
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ce qu'on propose au lancement qui suit un plantage.
 *
 * ### Pourquoi ici, et pas sur le moment
 *
 * Une application qui vient de planter n'a plus d'écran : la pile se déroule, le
 * processus est condamné, et ouvrir une boîte depuis un gestionnaire d'exception non
 * rattrapée ne marche qu'une fois sur deux — quand cela ne provoque pas un second
 * plantage par-dessus le premier ([D138][decisions]).
 *
 * Ce qui se fait sur le moment tient en une ligne : écrire la trace. La proposition
 * arrive ici, où l'application est entière et où quelqu'un regarde.
 *
 * ### Elle ne revient pas
 *
 * Accepter ou refuser **oublie la trace** dans les deux cas. Une proposition qu'on
 * décline et qui reviendrait à chaque lancement serait une punition pour un plantage
 * dont on n'est pas l'auteur, et l'on finirait par ne plus lire ce qu'elle propose.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun CrashReportOffer(viewModel: CrashOfferViewModel = hiltViewModel()) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    if (!pending) return

    val subject = stringResource(R.string.crash_subject)
    val intro = stringResource(R.string.crash_intro)

    AlertDialog(
        onDismissRequest = viewModel::onDecline,
        title = { Text(stringResource(R.string.crash_title)) },
        text = { Text(stringResource(R.string.crash_body)) },
        confirmButton = {
            TextButton(onClick = { viewModel.onSend(subject, intro) }) {
                Text(stringResource(R.string.crash_send))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::onDecline) { Text(stringResource(R.string.crash_decline)) }
        },
    )
}

/**
 * Sait s'il y a quelque chose à proposer.
 *
 * `false` au départ plutôt qu'un état « on ne sait pas » : la lecture du fichier prend
 * quelques millisecondes, et une boîte qui arrive juste après est moins gênante qu'un
 * écran qui attend un fichier pour s'afficher.
 */
@HiltViewModel
class CrashOfferViewModel @Inject constructor(
    private val crashes: CrashReports,
    private val reportCrash: ReportCrash,
) : ViewModel() {
    private val toOffer = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = toOffer.asStateFlow()

    init {
        viewModelScope.launch { toOffer.value = crashes.pending() != null }
    }

    /**
     * Ouvre le courriel, et referme la boîte **sans attendre** de savoir s'il s'est
     * ouvert.
     *
     * Une boîte qui resterait le temps d'un aller-retour vers l'application de
     * messagerie reviendrait au retour, et donnerait l'impression que rien n'a marché.
     */
    fun onSend(subject: String, intro: String) {
        toOffer.value = false
        viewModelScope.launch { runCatching { reportCrash(subject, intro) } }
    }

    /** Refuser oublie la trace : sans cela, la question reviendrait à chaque lancement. */
    fun onDecline() {
        toOffer.value = false
        viewModelScope.launch { runCatching { crashes.clear() } }
    }
}
