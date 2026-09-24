package app.hexavore.domain.usecase

import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.EntryDraft

/**
 * Enregistre un brouillon, qu'il ouvre un plat ou en corrige un.
 *
 * **Le choix entre les deux appartient au brouillon**, qui sait s'il désigne un plat
 * existant, et non à l'écran. La branche vivait dans le `ViewModel` ; elle y était
 * juste, mais elle y était aussi le seul endroit où l'écran avait à connaître la
 * différence entre créer et corriger. Chaque nouveau mode de saisie l'aurait recopiée.
 *
 * Rien de plus : les deux cas d'usage gardent chacun leurs règles, la source et
 * l'heure qui ne bougent pas ([D32][decisions]), le plat vidé qui se supprime
 * ([D61][decisions]), les fiches versées au catalogue.
 *
 * [decisions]: docs/11-decisions.md
 */
class SaveDraft(private val log: LogDish, private val update: UpdateDish) {
    /**
     * @return le plat écrit, ou `null` quand il n'y en a plus aucun : un brouillon
     *   vidé de sa dernière ligne supprime le plat qu'il modifiait ([D61][decisions]).
     *   L'appelant s'en sert pour ranger la photo sous le bon nom, et une photo rangée
     *   sous un plat qui vient de disparaître serait orpheline à la naissance.
     */
    suspend operator fun invoke(draft: EntryDraft): DishId? = when {
        !draft.editing -> log(draft)
        draft.emptying -> null.also { update(draft) }
        else -> draft.dishId.also { update(draft) }
    }
}
