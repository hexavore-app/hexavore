package app.hexavore.data.diary

import app.hexavore.core.database.dao.DishWithEntries
import app.hexavore.core.database.entity.DishEntity
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.MealMoment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Ce que la correspondance fait d'une valeur qu'elle ne reconnaît pas.
 *
 * **Le seul endroit où un repli se décide**, et il se décide deux fois ici : pour la
 * source d'un plat et pour son moment. Une base écrite par une version plus récente,
 * ou une ligne abîmée, ne doit jamais rendre le journal illisible — mais le repli ne
 * doit pas non plus **affirmer** quelque chose que personne n'a choisi.
 *
 * Les deux cas ne retombent donc pas au même endroit, et c'est délibéré : une saisie
 * manuelle n'invente aucune provenance automatique, là où un moment inconnu n'a pas
 * d'équivalent neutre — c'est l'heure du plat qui doit reprendre la main.
 */
class DiaryMapperTest {
    @Test
    fun `un moment inconnu se lit comme absent`() {
        // `BREAKFAST` comme repli afficherait « Petit-déjeuner » sur un dîner, avec
        // l'aplomb d'une valeur choisie -- et l'heure du plat ne pourrait plus rien y
        // faire, puisqu'elle ne reprend la main que sur un moment nul.
        assertNull(plat(moment = "BRUNCH").toDomain().moment)
    }

    @Test
    fun `un moment connu se lit tel quel`() {
        assertEquals(MealMoment.DINNER, plat(moment = "DINNER").toDomain().moment)
    }

    @Test
    fun `une source inconnue retombe sur la saisie manuelle`() {
        // Le pendant, et l'asymétrie assumée : « à la main » n'invente aucune
        // provenance automatique, donc c'est le repli le plus prudent qui existe.
        assertEquals(EntrySource.MANUAL, plat(source = "TELEPATHIE").toDomain().source)
    }

    private fun plat(source: String = "MANUAL", moment: String? = null) = DishWithEntries(
        dish = DishEntity(
            id = "d1",
            date = "2026-03-15",
            source = source,
            loggedAt = 0L,
            title = null,
            moment = moment,
            createdAt = 0L,
            updatedAt = 0L,
        ),
        entries = emptyList(),
    )
}
