package app.hexavore.feature.entry

import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.PhotoFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
     * Le jour vers lequel la dernière copie est partie, tant que l'écran ne l'a pas dit.
     *
     * Il revient à `null` dès que le message est passé : c'est une nouvelle, pas un
     * état, et la laisser traîner la ferait reparaître au prochain retour sur l'écran.
     */
    private val copie = MutableStateFlow<LocalDate?>(null)

    val copied: StateFlow<LocalDate?> = copie.asStateFlow()

    /** Le message est passé. */
    fun copyShown() {
        copie.value = null
    }

    /**
     * Recopie ce plat sur un autre jour, **sans quitter l'écran**.
     *
     * **L'original reste**, et c'est tout l'intérêt : un petit-déjeuner identique se
     * recopie, il ne se déménage pas.
     *
     * L'écran se refermait, par symétrie avec la suppression. C'était une erreur de
     * raisonnement : la suppression ferme parce qu'il n'y a plus rien à montrer, alors
     * qu'une copie **ne touche pas** le plat ouvert. Rester permet d'ailleurs ce que le
     * geste sert à faire — recopier le même petit-déjeuner sur trois matins — là où
     * fermer obligeait à rouvrir le plat entre chaque jour ([D144][decisions]).
     *
     * Le jour atteint est annoncé, parce qu'une copie ne se voit nulle part : elle
     * atterrit sur un écran qu'on ne regarde pas.
     *
     * [decisions]: docs/11-decisions.md
     */
    fun copyTo(date: LocalDate) {
        val source = dishId ?: return
        scope.launch {
            // Seul un succes s'annonce : dire « copie au 3 octobre » apres un echec
            // d'ecriture serait le seul endroit de l'application ou l'on mentirait.
            runCatching { filing.copyTo(source, date) }
                .getOrNull()
                ?.let { copie.value = date }
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
     * **L'échec se dit.** Il était silencieux, au motif qu'un message d'erreur sur un
     * bouton d'entraide serait un reproche de plus à quelqu'un qui rendait service. Sauf
     * que sans application de messagerie, le système ne dit rien non plus : l'appui ne
     * produisait alors **rien du tout**, et un bouton qui ne fait rien se signale bien
     * plus mal qu'une ligne de texte ([D153][decisions]). La barre nomme le manque, elle
     * ne reproche rien.
     *
     * [decisions]: docs/11-decisions.md
     */
    fun report(subject: String, body: String) {
        val current = form.value ?: return
        scope.launch {
            val ouvert = runCatching {
                filing.reporting.report(current.toDraft(), photo.value, subject, body)
            }.getOrDefault(false)
            if (!ouvert) echec.value = true
        }
    }

    /**
     * Le signalement n'a trouvé personne à qui parler, tant que l'écran ne l'a pas dit.
     *
     * Même forme que [copied] : une nouvelle, pas un état. Elle revient à `null` dès que
     * le message est passé.
     */
    private val echec = MutableStateFlow(false)

    val reportFailed: StateFlow<Boolean> = echec.asStateFlow()

    /** Le message est passé. */
    fun reportFailureShown() {
        echec.value = false
    }
}
