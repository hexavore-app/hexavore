package app.hexavore.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.hexavore.core.testing.TestDispatchers
import app.hexavore.data.diary.DishPhotoFiles
import app.hexavore.domain.backup.Snapshot
import app.hexavore.domain.backup.SnapshotCodec
import app.hexavore.domain.backup.SnapshotRead
import app.hexavore.domain.diary.DishId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * L'archive : ce qu'elle écrit, ce qu'elle relit, et ce qu'elle refuse de croire.
 *
 * **Le vrai dossier de photos, et un faux codec.** L'enveloppe est ce qui est en jeu ici
 * — l'ordre des entrées, le repli sur l'ancien format, ce qu'une archive bricolée à la
 * main peut faire écrire — et elle lit de vrais fichiers, donc le dépôt ne peut pas être
 * un faux en mémoire. La sérialisation du journal, elle, est éprouvée ailleurs : le
 * codec rend ici des octets reconnaissables et la réponse qu'on lui dicte.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class ZipSnapshotArchiveTest {
    private val dispatchers = TestDispatchers(Dispatchers.Unconfined)
    private val photos = DishPhotoFiles(ApplicationProvider.getApplicationContext<Context>(), dispatchers)
    private val codec = FakeCodec()
    private val archive = ZipSnapshotArchive(codec, photos, dispatchers)

    @Test
    fun `l archive porte le journal d abord, et les photos ensuite`() = runBlocking {
        garder(PREMIER, byteArrayOf(1, 2, 3))
        garder(SECOND, byteArrayOf(4, 5))

        val entrees = noms(ecrire())

        assertEquals("le journal vient en premier", "hexavore.json.gz", entrees.first())
        assertTrue(entrees.containsAll(listOf("photos/plat-1.jpg", "photos/plat-2.jpg")))
    }

    @Test
    fun `relire une archive rend le journal et repose les photos`() = runBlocking {
        garder(PREMIER, byteArrayOf(1, 2, 3))
        val octets = ecrire()
        photos.forgetAll()

        val lu = archive.read(octets.inputStream())

        assertTrue(lu is SnapshotRead.Readable)
        assertEquals(setOf(PREMIER), gardees())
    }

    @Test
    fun `un ancien fichier sans archive reste lisible`() = runBlocking {
        // Un export ecrit avant que les photos existent est un gzip nu. Il se reconnait
        // a ses premiers octets, et il ne porte aucune image.
        val lu = archive.read(JOURNAL.inputStream())

        assertTrue(lu is SnapshotRead.Readable)
        assertTrue("un ancien fichier n'a pas de photo a reposer", gardees().isEmpty())
    }

    @Test
    fun `un journal trop recent n ecrit aucune photo`() = runBlocking {
        // La regle qui justifie que le journal soit la premiere entree : refuser un
        // fichier ne doit pas laisser des images derriere.
        garder(PREMIER, byteArrayOf(1, 2, 3))
        val octets = ecrire()
        photos.forgetAll()
        codec.read = SnapshotRead.TooRecent(99)

        val lu = archive.read(octets.inputStream())

        assertEquals(SnapshotRead.TooRecent(99), lu)
        assertTrue("rien n'a ete repose", gardees().isEmpty())
    }

    @Test
    fun `une entree mal nommee est laissee`() = runBlocking {
        // Une archive est un fichier que l'utilisateur a pu ouvrir et modifier. La seule
        // reponse sure a une entree qu'on ne reconnait pas est de la laisser.
        val bricolee = ByteArrayOutputStream()
        ZipOutputStream(bricolee).use { zip ->
            zip.putNextEntry(ZipEntry("hexavore.json.gz"))
            zip.write(JOURNAL)
            zip.closeEntry()
            listOf("photos/../evade.jpg", "photos/plat 3.jpg", "lisezmoi.txt").forEach { nom ->
                zip.putNextEntry(ZipEntry(nom))
                zip.write(byteArrayOf(9))
                zip.closeEntry()
            }
        }

        archive.read(bricolee.toByteArray().inputStream())

        assertTrue(gardees().isEmpty())
        assertNull("et rien n'attend non plus dans le depot", photos.staged())
    }

    private suspend fun garder(dish: DishId, jpeg: ByteArray) {
        photos.stage(jpeg)
        photos.attach(dish)
    }

    private suspend fun gardees(): Set<DishId> = photos.observeKept().first().keys

    private suspend fun ecrire(): ByteArray = ByteArrayOutputStream().also { archive.write(VIDE, it) }.toByteArray()

    private fun noms(archive: ByteArray): List<String> = ZipInputStream(archive.inputStream()).use { zip ->
        generateSequence { zip.nextEntry }.map { it.name }.toList()
    }

    /** Le codec, réduit à des octets reconnaissables et à la réponse qu'on lui dicte. */
    private class FakeCodec(var read: SnapshotRead = SnapshotRead.Readable(VIDE)) : SnapshotCodec {
        override suspend fun encode(snapshot: Snapshot): ByteArray = JOURNAL

        override suspend fun decode(bytes: ByteArray): SnapshotRead = read
    }

    private companion object {
        val PREMIER = DishId("plat-1")
        val SECOND = DishId("plat-2")

        val VIDE = Snapshot(exportedAt = Instant.parse("2026-09-23T12:00:00Z"), appVersion = "0.4")

        /** Un en-tete de gzip, pour que le lecteur ne prenne pas ce fichier pour un zip. */
        val JOURNAL = byteArrayOf(0x1f, 0x8b.toByte(), 0x08, 0x00, 0x00)
    }
}
