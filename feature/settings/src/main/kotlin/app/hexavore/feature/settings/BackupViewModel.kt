package app.hexavore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.time.Clock
import app.hexavore.domain.usecase.EraseEverything
import app.hexavore.domain.usecase.ExportArchive
import app.hexavore.domain.usecase.RestoreArchive
import app.hexavore.domain.usecase.RestoreOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * Exporter, restaurer, tout effacer.
 *
 * **Les flux traversent, les fichiers non.** Le `ViewModel` ne connaît ni `Uri` ni
 * `ContentResolver` : l'écran ouvre le document que le système lui a donné et n'en fait
 * passer ici qu'un flux. C'est ce qui permet à ces trois gestes de se tester sans
 * Android.
 *
 * **Des flux et non des tableaux d'octets**, depuis que l'archive emporte les photos :
 * un an d'images pèse deux cents mégaoctets, et les tenir en mémoire entre la capture et
 * l'écriture ferait tomber l'application chez ceux qui ont le plus à sauvegarder.
 *
 * **L'export se fait en deux temps, et l'ordre compte.** Le journal est figé *avant*
 * que le document s'ouvre : l'ordre inverse écrirait l'état de l'application au moment
 * où le fichier a fini de s'ouvrir, et non celui où l'export a été demandé.
 */
@HiltViewModel
internal class BackupViewModel @Inject constructor(
    private val exportArchive: ExportArchive,
    private val restoreArchive: RestoreArchive,
    private val eraseEverything: EraseEverything,
    private val clock: Clock,
) : ViewModel() {
    private val state = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = state.asStateFlow()

    /** Le nom proposé au sélecteur, calculé sur l'horloge du domaine et non sur `now()`. */
    fun proposedName(): String = backupFileName(clock.today())

    /**
     * Écrit l'archive dans le document que [open] vient d'ouvrir.
     *
     * `null` quand le document n'a pas pu être ouvert : refusé, disparu, ou sur un
     * support débranché entre le choix et l'écriture.
     */
    fun onExport(open: () -> OutputStream?) = working {
        // La capture d'abord, l'ouverture du document ensuite : ce qui part decrit
        // l'instant de la demande. Les photos, elles, sont lues a l'ecriture.
        val pending = exportArchive()
        val sink = open() ?: return@working BackupMessage.ExportFailed
        runCatching { sink.use { pending.writeTo(it) } }
            .fold({ BackupMessage.Exported(it) }, { BackupMessage.ExportFailed })
    }

    fun onImport(open: () -> InputStream?) = working {
        // Le document n'a pas pu etre ouvert : c'est indiscernable d'un fichier
        // illisible pour qui regarde l'ecran, et les deux se rattrapent pareil -- en
        // choisissant un autre fichier.
        val source = open() ?: return@working BackupMessage.Unreadable
        when (val outcome = source.use { restoreArchive(it) }) {
            is RestoreOutcome.Restored -> BackupMessage.Restored(outcome.entryCount)
            is RestoreOutcome.TooRecent -> BackupMessage.TooRecent(outcome.formatVersion)
            RestoreOutcome.Unreadable -> BackupMessage.Unreadable
            RestoreOutcome.Failed -> BackupMessage.RestoreFailed
        }
    }

    fun onErase() = working {
        eraseEverything()
        BackupMessage.Erased
    }

    /** Le compte rendu a été lu ; il ne doit pas réapparaître à la rotation de l'écran. */
    fun onMessageShown() = state.update { it.copy(message = null) }

    /**
     * **Un seul travail à la fois**, et le geste refusé ne dit rien.
     *
     * Refuser en silence plutôt qu'afficher « patientez » : les trois boutons sont
     * désactivés pendant le travail, donc un second geste ne peut venir que d'une
     * course, et une course n'a pas de message à donner à qui que ce soit.
     */
    private fun working(block: suspend () -> BackupMessage) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val message = block()
            state.update { it.copy(busy = false, message = message) }
        }
    }
}
