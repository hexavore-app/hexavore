package app.hexavore.domain.diary

import app.hexavore.domain.resolution.MatchVerdict
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Le doute du modèle, et le seuil où il se signale (D162).
 *
 * Une confiance de 20 % s'affichait comme une de 95 % : le seuil est celui où le
 * résolveur cesse de remplir seul, et ces cas le tiennent des deux côtés.
 */
class SuggestionTest {
    @Test
    fun `une confiance faible se signale`() {
        assertTrue(proposition(confiance = 0.2f).unsure)
    }

    @Test
    fun `juste sous le seuil, on doute encore`() {
        assertTrue(proposition(confiance = 0.74f).unsure)
    }

    @Test
    fun `au seuil, la proposition se lit sans marque`() {
        assertFalse(proposition(confiance = 0.75f).unsure)
        assertFalse(proposition(confiance = 0.95f).unsure)
    }

    private fun proposition(confiance: Float) = Suggestion(
        confidence = confiance,
        verdict = MatchVerdict.AUTOMATIC,
        alternatives = emptyList(),
        estimated = false,
    )
}
