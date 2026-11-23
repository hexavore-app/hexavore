package app.hexavore.domain.report

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Une pièce jointe se compare par son contenu.
 *
 * Elle voyage dans des états d'écran : une égalité par référence ferait recomposer
 * un écran à chaque émission, et deux pièces identiques ne seraient jamais égales.
 */
class AttachmentTest {
    @Test
    fun `deux pieces de meme contenu sont egales, et de meme empreinte`() {
        val une = Attachment("trace.txt", "text/plain", byteArrayOf(1, 2, 3))
        val autre = Attachment("trace.txt", "text/plain", byteArrayOf(1, 2, 3))

        assertEquals(une, autre)
        assertEquals(une.hashCode(), autre.hashCode())
    }

    @Test
    fun `un octet, un nom ou un type de plus les distingue`() {
        val base = Attachment("trace.txt", "text/plain", byteArrayOf(1, 2, 3))

        assertNotEquals(base, base.copy(bytes = byteArrayOf(1, 2, 4)))
        assertNotEquals(base, base.copy(name = "autre.txt"))
        assertNotEquals(base, base.copy(mimeType = "application/zip"))
        assertNotEquals(base, "trace.txt")
    }
}
