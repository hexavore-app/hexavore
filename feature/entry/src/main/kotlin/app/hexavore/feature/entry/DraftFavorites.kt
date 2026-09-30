package app.hexavore.feature.entry

import app.hexavore.domain.diary.EntryDraft
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.usecase.FavoriteOutcome
import app.hexavore.domain.usecase.ProposeFavoriteName
import app.hexavore.domain.usecase.RemoveFavoriteDish
import app.hexavore.domain.usecase.SaveFavoriteDish
import app.hexavore.domain.usecase.UpdateFavoriteDish
import javax.inject.Inject

/**
 * Les trois gestes que l'étoile déclenche, en un objet.
 *
 * **Un regroupement et non une couche de plus** : ce sont trois cas d'usage du
 * domaine, passés ensemble parce qu'ils répondent à une même question — que fait
 * l'étoile de ce plat. Passés un par un, ils poussaient le constructeur du `ViewModel`
 * au-delà du seuil de paramètres, et la réponse du projet est de regrouper selon ce
 * que les choses sont plutôt que de relever le seuil.
 */
class DraftFavorites @Inject constructor(
    private val saveFavoriteDish: SaveFavoriteDish,
    private val removeFavoriteDish: RemoveFavoriteDish,
    private val proposeFavoriteName: ProposeFavoriteName,
    private val updateFavoriteDish: UpdateFavoriteDish,
) {
    suspend fun save(draft: EntryDraft, name: String, existing: FavoriteDishId?): FavoriteOutcome =
        saveFavoriteDish(draft, name, existing)

    suspend fun remove(id: FavoriteDishId) = removeFavoriteDish(id)

    /**
     * Réécrit le modèle que ce brouillon décrit, et délie les plats qui le citaient.
     *
     * **L'identifiant vient de la route, jamais du brouillon**, et c'est ce qui répare
     * une modification qui échouait toujours. Le lien qu'un brouillon porte vers un
     * favori dit *« ce plat vient de ce modèle »*, et il tombe dès qu'une ligne bouge
     * ([D62][decisions]) — ce qui est juste pour un plat rejoué, et faux ici : toucher
     * une ligne **est** le geste qu'on vient faire. Le lire dans le brouillon revenait
     * donc à exiger qu'on n'ait rien modifié pour pouvoir enregistrer une
     * modification, et l'écran répondait « écriture non aboutie » à chaque fois
     * ([D135][decisions]).
     *
     * **Un favori disparu est un échec**, pas un succès silencieux : l'écran garde la
     * saisie et propose de réessayer, là où ne rien dire aurait laissé croire que la
     * correction était enregistrée.
     *
     * [decisions]: docs/11-decisions.md
     */
    suspend fun rewrite(draft: EntryDraft, id: FavoriteDishId) {
        checkNotNull(updateFavoriteDish(draft, id)) { "Favori disparu pendant la modification." }
    }

    /** Le titre du plat, ou son premier rang libre : « Déjeuner 2 ». Les mots viennent de l'écran. */
    suspend fun propose(base: String, numbered: (String, Int) -> String): String = proposeFavoriteName(base, numbered)
}
