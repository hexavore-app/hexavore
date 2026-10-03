package app.hexavore.feature.entry

import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.PhotoFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Les trois gestes de l'en-tête, et ce qu'ils font à l'écran.
 *
 * ### Un porteur, comme le nommage d'un favori
 *
 * Ils vivaient dans le `ViewModel`, qui a fini par dépasser le seuil de fonctions —
 * lequel dit précisément qu'une classe fait trop de choses à la fois. Le découpage suit
 * ce que les choses sont : copier, supprimer et signaler portent tous sur **le plat
 * entier**, là où le reste du `ViewModel` modifie des lignes ([D143][decisions]).
 *
 * ### Les trois referment l'écran, ou s'en vont
 *
 * Copier écrit ailleurs, supprimer efface, signaler ouvre un courriel. Aucun des trois
 * ne laisse l'écran dans un état qu'on voudrait continuer de modifier — et c'est
 * pourquoi les deux premiers le ferment.
 *
 * [decisions]: docs/11-decisions.md
 */
internal class DraftHeaderActions(
    private val filing: DraftFiling,
    private val dishId: DishId?,
    private val scope: CoroutineScope,
    private val form: StateFlow<EntryForm?>,
    private val photo: StateFlow<PhotoFile?>,
    private val status: MutableStateFlow<EntryViewModel.Status>,
) {
    /**
     * Recopie ce plat sur un autre jour, et referme l'écran.
     *
     * **L'original reste**, et c'est tout l'intérêt : un petit-déjeuner identique se
     * recopie, il ne se déménage pas. L'écran se referme parce qu'il montrait le plat
     * d'origine, que la copie n'a pas changé.
     */
    fun copyTo(date: LocalDate) {
        val source = dishId ?: return
        scope.launch {
            runCatching { filing.copyTo(source, date) }
            status.value = EntryViewModel.Status.SAVED
        }
    }

    /**
     * Supprime ce plat.
     *
     * Le même geste que vider ses lignes une à une ([D61][decisions]), en un bouton :
     * c'était le seul chemin, et il demandait de comprendre qu'un plat vide se
     * supprime.
     *
     * [decisions]: docs/11-decisions.md
     */
    fun delete() {
        val source = dishId ?: return
        scope.launch {
            runCatching { filing.delete(source) }
            status.value = EntryViewModel.Status.SAVED
        }
    }

    /**
     * Signale la proposition affichée.
     *
     * **Ce qui part est ce que le mode debug montre déjà** : l'échange entier et la
     * photo ([D138][decisions]). Rien ne part sans un geste — l'application ouvre un
     * courriel prérempli, et c'est l'utilisateur qui appuie sur « envoyer ».
     *
     * **Les libellés viennent de l'écran** : ce porteur ne connaît pas de ressources.
     *
     * L'échec est silencieux : sans application de messagerie, il n'y a rien à dire de
     * plus que ce que le système dira lui-même, et un message d'erreur sur un bouton
     * d'entraide serait un reproche de plus à quelqu'un qui rendait service.
     *
     * [decisions]: docs/11-decisions.md
     */
    fun report(subject: String, body: String) {
        val current = form.value ?: return
        scope.launch {
            runCatching { filing.reporting.report(current.toDraft(), photo.value, subject, body) }
        }
    }
}
