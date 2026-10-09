package app.hexavore.domain.usecase

import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.EntryId
import app.hexavore.domain.identity.IdGenerator
import app.hexavore.domain.time.Clock
import java.time.LocalDate

/**
 * Le même plat, un autre jour.
 *
 * ### Une copie, et non un déplacement
 *
 * L'original reste où il est. C'est ce qui sert : un petit-déjeuner identique tous les
 * matins se recopie, il ne se déménage pas ([D143][decisions]). Et une erreur de date
 * ne coûte alors qu'une suppression, là où un déplacement aurait fait disparaître le
 * plat du jour où on le cherchait.
 *
 * ### De nouveaux identifiants, et une nouvelle heure
 *
 * Le plat copié est un **autre plat** : il a son identifiant, ses lignes ont les leurs,
 * et il est noté à l'instant où on le copie. Réutiliser les identifiants d'origine
 * aurait écrasé le plat qu'on voulait garder.
 *
 * **Le lien au favori ne suit pas.** Un favori décrit un modèle ; le plat recopié n'en
 * vient pas, il vient d'un autre plat. Garder le lien aurait rallumé l'étoile sur une
 * copie que personne n'a enregistrée comme favori ([D62][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
class CopyDishToDate(private val diary: DiaryRepository, private val clock: Clock, private val ids: IdGenerator) {
    /** @return l'identifiant de la copie, ou `null` si le plat d'origine a disparu. */
    suspend operator fun invoke(source: DishId, date: LocalDate): DishId? {
        val original = diary.dish(source) ?: return null
        val copie = DishId(ids.next())

        diary.save(
            original.copy(
                id = copie,
                date = date,
                loggedAt = clock.now(),
                favoriteId = null,
                entries = original.entries.map { it.copy(id = EntryId(ids.next()), dishId = copie) },
            ),
        )
        return copie
    }
}
