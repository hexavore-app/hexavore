package app.hexavore.feature.entry

import app.hexavore.domain.usecase.FavoriteOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Ce que la boîte de nommage montre : le nom proposé, et s'il vient d'être refusé.
 *
 * **Un seul objet pour les deux**, et ce n'est pas une commodité : `combine` ne lit
 * que ce qu'on lui donne, et le nom proposé vivait à côté — l'état de l'écran ne se
 * recalculait donc pas quand il changeait, et la boîte ne s'ouvrait que par accident,
 * à la faveur d'une autre émission. Ce qui appartient à la même question entre dans le
 * même flux ([D118][decisions]).
 *
 * `proposal` à `null` **ferme** la boîte : le nom se lit dans la liste des favoris —
 * « Déjeuner » y est peut-être déjà pris — donc il n'est connu qu'après une lecture,
 * et le champ, non contrôlé ([D45][decisions]), garde le texte de sa première
 * composition. Une boîte ouverte d'avance resterait vide pour de bon.
 *
 * [decisions]: docs/11-decisions.md
 */
internal data class Naming(val proposal: String? = null, val taken: Boolean = false)

/**
 * L'étoile du brouillon, et la boîte qui lui demande un nom.
 *
 * **Sortie du `ViewModel` quand il a dépassé le seuil de gestes**, et le découpage suit
 * ce que les choses sont : ce fichier porte le **favori** — le nom qu'on lui propose,
 * celui qu'on refuse, l'étoile qu'on éteint —, là où le `ViewModel` porte la saisie
 * elle-même. C'est le raisonnement de `DraftComposition` et de `DishGestures`, appliqué
 * une troisième fois.
 *
 * Elle écrit dans le formulaire plutôt que de le rendre : c'est elle qui sait qu'un
 * favori enregistré rattache le brouillon, et qu'une étoile éteinte l'en détache.
 */
internal class DraftNaming(
    private val favorites: DraftFavorites,
    private val scope: CoroutineScope,
    private val form: MutableStateFlow<EntryForm?>,
) {
    private val state = MutableStateFlow(Naming())

    val naming: StateFlow<Naming> = state.asStateFlow()

    /**
     * La boîte s'ouvre : on cherche un nom libre à partir du titre du plat.
     *
     * [base] et [numbered] viennent de l'écran parce que les mots sont des ressources
     * et que le domaine n'en écrit pas. Lui, il sait lesquels sont pris.
     *
     * **La boîte s'ouvre sur le nom, jamais avant lui.** Poser le titre d'abord puis le
     * corriger aurait deux écrivains pour une seule valeur, et le champ étant non
     * contrôlé, c'est le premier qui gagnerait à l'affichage — donc « Déjeuner » dans
     * une boîte qui voulait dire « Déjeuner 2 ». La lecture des favoris est locale ; ce
     * qu'elle coûte ne se voit pas.
     */
    fun propose(base: String, numbered: (String, Int) -> String) {
        scope.launch {
            state.value = Naming(proposal = runCatching { favorites.propose(base, numbered) }.getOrDefault(base))
        }
    }

    /** La boîte se referme, sans rien enregistrer. */
    fun dismiss() {
        state.value = Naming()
    }

    /**
     * Met le plat en favori sous ce nom.
     *
     * Un nom déjà pris est une **réponse**, pas une panne : la boîte reste ouverte avec
     * le nom refusé dedans, et c'est ce qui permet de le corriger plutôt que de tout
     * retaper ([D62][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    fun save(name: String) {
        val current = form.value ?: return
        state.update { it.copy(taken = false) }

        scope.launch {
            val outcome = runCatching { favorites.save(current.toDraft(), name, current.favoriteId) }
            when (val result = outcome.getOrNull()) {
                is FavoriteOutcome.Saved -> {
                    form.update { it?.copy(favoriteId = result.id) }
                    // La boite se referme sur l'ecriture aboutie, et sur elle seule :
                    // c'est le seul signal fiable, et il vient d'ici.
                    state.value = Naming()
                }

                FavoriteOutcome.NameTaken -> state.update { it.copy(taken = true) }
                null -> Unit
            }
        }
    }

    /**
     * Éteindre l'étoile **supprime le favori**.
     *
     * C'est le seul chemin pour retirer un plat de sa liste : la liste des favoris ne
     * sert qu'à en choisir un, et lui ajouter un geste de suppression aurait fait deux
     * endroits pour la même décision ([D62][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    fun remove() {
        val id = form.value?.favoriteId ?: return
        form.update { it?.copy(favoriteId = null) }
        scope.launch { runCatching { favorites.remove(id) } }
    }
}
