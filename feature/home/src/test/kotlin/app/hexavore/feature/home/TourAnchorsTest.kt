package app.hexavore.feature.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import app.hexavore.feature.home.tour.seenFrom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Le trou du voile tombe sur sa cible, d'où que le voile se dessine (D171). */
class TourAnchorsTest {
    @Test
    fun `un voile pose au coin de l ecran voit l ancre telle quelle`() {
        val ancre = Rect(left = 40f, top = 900f, right = 1040f, bottom = 1600f)

        assertEquals(ancre, ancre.seenFrom(Offset.Zero))
    }

    @Test
    fun `un voile ecarte du bord par l encoche voit l ancre decalee d autant`() {
        val ancre = Rect(left = 166f, top = 900f, right = 1166f, bottom = 1600f)

        assertEquals(
            Rect(left = 40f, top = 900f, right = 1040f, bottom = 1600f),
            ancre.seenFrom(Offset(x = 126f, y = 0f)),
        )
    }
}
