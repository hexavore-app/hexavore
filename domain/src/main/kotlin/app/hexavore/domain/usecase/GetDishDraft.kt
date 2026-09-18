package app.hexavore.domain.usecase

import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.DraftLine
import app.hexavore.domain.diary.DraftLineId
import app.hexavore.domain.diary.EntryDraft
import app.hexavore.domain.diary.QuantityUnit
import app.hexavore.domain.diary.momentIn
import app.hexavore.domain.identity.IdGenerator
import app.hexavore.domain.nutrition.NutrientValues
import app.hexavore.domain.time.Clock

/**
 * Rouvre un plat enregistré sous la forme que l'écran de validation manipule.
 *
 * **Les macros rendues sont celles qui ont été figées à l'enregistrement**, jamais
 * recalculées depuis une fiche d'aliment. Un fabricant qui reformule son produit ne
 * doit pas réécrire un journal vieux de six mois ([D05][decisions]) : rouvrir une
 * ligne pour corriger une quantité ne doit pas non plus être l'occasion de la
 * réécrire en silence.
 *
 * Chaque ligne conserve son [EntryId][app.hexavore.domain.diary.EntryId] et reçoit
 * un identifiant de brouillon neuf. Les deux ne se confondent pas : le premier dit
 * quelle ligne du journal sera réécrite, le second sert à la désigner à l'écran.
 *
 * [decisions]: docs/11-decisions.md
 */
class GetDishDraft(private val diary: DiaryRepository, private val ids: IdGenerator, private val clock: Clock) {
    /** @return `null` si le plat n'existe plus. */
    suspend operator fun invoke(dishId: DishId): EntryDraft? {
        val dish = diary.dish(dishId) ?: return null
        return EntryDraft(
            dishId = dish.id,
            date = dish.date,
            source = dish.source,
            title = dish.title,
            // Le moment retenu a la saisie, ou celui que dit l'heure du plat pour ceux
            // d'avant. Le champ de l'ecran montre ainsi le nom que l'accueil affichait,
            // et non un nom calcule sur l'heure qu'il est maintenant.
            moment = dish.momentIn(clock.zone()),
            // C'est lui qui rallume l'etoile. Il tombera a la premiere ligne touchee.
            favoriteId = dish.favoriteId,
            lines = dish.entries.map { entry ->
                DraftLine(
                    id = DraftLineId(ids.next()),
                    entryId = entry.id,
                    foodId = entry.foodId,
                    name = entry.displayName,
                    quantity = entry.quantity,
                    // La portion nommee se reconstruit depuis ce qui a ete ecrit,
                    // sans relire la fiche : elle a pu etre supprimee depuis, et un
                    // plat de l'an dernier doit rester relisible tel qu'il a ete
                    // enregistre.
                    unit = QuantityUnit.of(entry.unit, entry.grams, entry.quantity),
                    values = NutrientValues.of(entry.macros),
                    // Reconstruite depuis ce qui a ete ecrit, sans relire la fiche :
                    // elle a pu etre corrigee ou supprimee depuis, et un journal est
                    // un registre d evenements.
                    reference = DraftLine.referenceOf(NutrientValues.of(entry.macros), entry.grams),
                )
            },
        )
    }
}
