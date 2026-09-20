package app.hexavore.domain.usecase

import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.EntryDraft
import app.hexavore.domain.diary.toEntries
import app.hexavore.domain.identity.IdGenerator

/**
 * Réécrit le contenu d'un plat déjà enregistré.
 *
 * **La source et l'heure du plat existant sont conservées, et ce n'est pas un
 * oubli.** L'origine d'un plat est un fait historique, pas un état : corriger une
 * quantité sur une proposition de l'IA ne doit pas la faire passer pour une saisie
 * manuelle, sans quoi on perd la seule trace de ce qui a été deviné. Le cas d'usage
 * ne lit donc jamais `draft.source`, ce qui rend l'erreur impossible plutôt que
 * simplement déconseillée ([D32][decisions]).
 *
 * L'heure suit le même raisonnement : elle dit quand le plat a été mangé, pas quand
 * il a été corrigé. La remettre à jour ferait sauter le plat en bas de la journée à
 * chaque relecture.
 *
 * **Un brouillon vidé de ses lignes supprime le plat.** C'est la même règle que
 * `DeleteEntry` applique déjà à la dernière ligne d'un plat, et elle vaut ici pour la
 * même raison : un plat sans contenu n'est pas un plat à zéro calorie, c'est une saisie
 * qui n'a pas eu lieu. La faire tenir aux deux endroits évite qu'un même geste — retirer
 * les lignes une à une — réussisse par un chemin et se fasse refuser par l'autre.
 *
 * [decisions]: docs/11-decisions.md
 *
 * @see docs/06-architecture.md
 */
class UpdateDish(private val diary: DiaryRepository, private val ids: IdGenerator) {
    /**
     * @throws IllegalArgumentException si le brouillon est incomplet ou ne désigne
     *   aucun plat.
     * @throws IllegalStateException si le plat désigné n'existe plus — supprimé
     *   depuis un autre écran pendant l'édition. Le cas ne se pose pas pour un
     *   brouillon vidé : supprimer ce qui a déjà disparu n'a rien à vérifier.
     */
    suspend operator fun invoke(draft: EntryDraft) {
        require(draft.saveable) { "Brouillon incomplet : chaque ligne demande un nom, une quantite et une energie." }
        val id = requireNotNull(draft.dishId) { "Brouillon sans plat d'origine : utiliser LogDish." }

        if (draft.lines.isEmpty()) {
            diary.deleteDish(id)
        } else {
            val existing = checkNotNull(diary.dish(id)) { "Plat introuvable : ${id.value}" }
            diary.save(
                existing.copy(
                    date = draft.date,
                    entries = draft.toEntries(id, ids),
                    // Le titre et le moment viennent du brouillon : ce sont les deux
                    // seules choses de l'en-tete qu'on vient corriger. La source et
                    // l'heure, elles, restent celles du plat existant.
                    title = draft.title?.trim()?.takeIf { it.isNotEmpty() },
                    moment = draft.moment,
                    // Le lien vers le favori vient du brouillon et non du plat
                    // existant : il tombe des qu'une ligne est touchee, et c'est
                    // precisement cette chute qu'il faut enregistrer.
                    favoriteId = draft.favoriteId,
                ),
            )
        }
    }
}
