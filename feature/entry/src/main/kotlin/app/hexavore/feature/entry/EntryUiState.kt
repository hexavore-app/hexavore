package app.hexavore.feature.entry

import app.hexavore.domain.diary.DraftImpact
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.PhotoFile
import app.hexavore.domain.profile.UnitSystem
import java.time.LocalDate

/**
 * L'état de l'écran de validation.
 *
 * @see docs/06-architecture.md
 */
internal sealed interface EntryUiState {
    /** Avant que le plat à modifier n'ait été relu. Immédiat pour une saisie neuve. */
    data object Loading : EntryUiState

    data class Content(
        val form: EntryForm,
        /**
         * Le système d'unités choisi, **pour le sélecteur d'une ligne et rien d'autre**.
         *
         * Il ne convertit rien : ce qu'une ligne porte reste ce qui a été saisi, et ce
         * réglage décide seulement de la paire qu'on propose d'ajouter.
         */
        val units: UnitSystem,
        /**
         * `null` quand la journée n'a pas pu être lue.
         *
         * L'écran renonce alors au « il vous restera », et à rien d'autre : refuser
         * la saisie parce qu'on n'a pas pu lire le reste de la journée ferait perdre
         * le repas pour une information de confort.
         */
        val impact: DraftImpact?,
        /** `true` pendant l'écriture : le bouton d'enregistrement devient inerte. */
        val saving: Boolean = false,
        /**
         * `true` quand le dernier nom de favori proposé était déjà pris.
         *
         * Porté par l'état plutôt que rendu par l'appel : la boîte de nommage reste
         * ouverte avec le nom refusé dedans, et c'est ce qui permet de le corriger
         * plutôt que de tout retaper.
         */
        val favoriteNameTaken: Boolean = false,
        /**
         * Le nom proposé pour ce favori, ou `null` quand la boîte est fermée.
         *
         * **C'est lui qui ouvre la boîte.** Le nom dépend d'une lecture — « Déjeuner »
         * est peut-être déjà pris — et le champ de la boîte est non contrôlé : il
         * garde le texte de sa première composition, donc la boîte ne peut pas
         * s'ouvrir avant de savoir quoi y écrire.
         */
        val favoriteProposal: String? = null,
        /**
         * `true` quand on modifie **le favori lui-même** et non un repas.
         *
         * L'écran est le même ; ce qui change est ce qu'enregistrer veut dire, et il
         * doit le dire — son titre comme son bouton. Un écran qui annonce « Nouvelle
         * saisie » alors qu'il réécrit un modèle ment sur ce que l'appui va faire.
         */
        val editingFavorite: Boolean = false,
        /**
         * Le jour d'aujourd'hui, pour savoir si le brouillon en vise un autre.
         *
         * Il vient du modèle et non d'un `LocalDate.now()` dans l'écran : c'est la
         * règle du projet, et c'est aussi ce qui rend le cas éprouvable.
         */
        val today: LocalDate? = null,
        /**
         * Le jour ou la derniere copie est partie, tant que l'ecran ne l'a pas dit.
         *
         * Une copie n'a aucune trace visible : elle atterrit sur une journee qu'on ne
         * regarde pas, et sans ce mot l'appui sur « Copier » ne produirait rien de
         * perceptible ([D144][decisions]).
         *
         * [decisions]: docs/11-decisions.md
         */
        val copiedTo: LocalDate? = null,
        /**
         * `true` quand un signalement n'a trouve aucune application de messagerie.
         *
         * Une nouvelle, comme [copiedTo] : l'ecran la dit une fois, puis l'oublie.
         */
        val reportFailed: Boolean = false,
        /**
         * La photo de ce plat, ou celle que le scan ou l'analyse viennent d'apporter.
         *
         * `null` dans le cas courant : une saisie manuelle, une recherche et un favori
         * rejoué n'ont aucune image à montrer. `null` aussi dès que la croix a été
         * touchée, sans que rien n'ait encore été effacé sur le disque.
         */
        val photo: PhotoFile? = null,
    ) : EntryUiState {
        /**
         * Le jour que cette saisie va écrire, **quand ce n'est pas aujourd'hui**.
         *
         * `null` pour aujourd'hui : l'écran n'affiche alors aucune date, comme avant.
         * Depuis qu'on peut rattraper un repas oublié, le brouillon peut viser un jour
         * passé — et c'est ici, juste au-dessus du bouton d'enregistrement, qu'il faut
         * le dire. L'accueil le montre déjà en titre, mais entre les deux il y a eu la
         * recherche, un scan, ou une modale d'IA.
         */
        val otherDay: LocalDate? get() = form.date.takeIf { today != null && it != today }

        /** `true` quand ce plat est dans la liste des favoris. */
        val favorite: Boolean get() = form.favoriteId != null

        /**
         * Un plat sans ligne enregistrable ne peut pas devenir un favori.
         *
         * Il ne rejouerait rien. L'étoile est donc absente plutôt que refusante : il
         * n'y a rien à expliquer sur un brouillon qu'on est en train de remplir.
         */
        val favoritable: Boolean get() = form.toDraft().let {
            it.lines.isNotEmpty() && it.lines.all { l -> l.complete }
        }
        val saveable: Boolean get() = !saving && form.toDraft().saveable

        /**
         * Y a-t-il quelque chose à signaler ?
         *
         * **Seulement ce qu'un modèle vient de proposer, et pas encore enregistré.**
         *
         * La première condition tient depuis [D138][decisions] : il n'y a rien à
         * signaler d'une saisie qu'on a faite soi-même, ni d'un code-barres dont les
         * valeurs viennent d'Open Food Facts.
         *
         * La seconde est nouvelle. Le bouton survivait à l'enregistrement, et restait
         * sur un plat qu'on avait relu, corrigé, validé : ce qui partait n'était alors
         * plus ce que le modèle avait proposé, et le signalement rapportait une erreur
         * que la correction avait déjà effacée ([D150][decisions]). Un plat déjà écrit
         * porte un identifiant ; une proposition n'en a pas encore.
         *
         * [decisions]: docs/11-decisions.md
         */
        val reportable: Boolean
            get() = form.dishId == null && (form.source == EntrySource.PHOTO_AI || form.source == EntrySource.TEXT_AI)

        /**
         * `true` quand enregistrer **supprimerait** le plat, faute de ligne restante.
         *
         * L'écran s'en sert pour changer le libellé du bouton. Sans cela, retirer la
         * dernière ligne puis appuyer sur « Enregistrer » ferait disparaître le plat
         * sans que rien ne l'ait annoncé — et le geste est le même que celui qui, une
         * ligne plus tôt, enregistrait une correction.
         */
        val emptying: Boolean get() = form.toDraft().emptying
    }

    /**
     * Le plat n'a pas pu être rouvert.
     *
     * Supprimé pendant qu'on le modifiait, ou illisible. Le cas n'est pas théorique :
     * la suppression par balayage de l'accueil est immédiate, et rien n'empêche
     * d'avoir ouvert la modification avant. Les deux causes appellent le même geste
     * — quitter l'écran — donc elles ne sont pas distinguées ; annoncer « supprimé »
     * quand la base est simplement illisible serait pire qu'imprécis, ce serait faux.
     */
    data object Unavailable : EntryUiState

    /** L'écriture a échoué. Le brouillon est conservé, il n'y a qu'à réessayer. */
    data class Error(val form: EntryForm) : EntryUiState

    /** Écrit. L'écran se referme. */
    data object Saved : EntryUiState
}
