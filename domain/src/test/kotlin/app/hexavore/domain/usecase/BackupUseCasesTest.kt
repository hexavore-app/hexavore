package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryBackupTarget
import app.hexavore.core.testing.InMemoryDishPhotos
import app.hexavore.domain.backup.BACKUP_ROTATION
import app.hexavore.domain.backup.Snapshot
import app.hexavore.domain.backup.SnapshotArchive
import app.hexavore.domain.backup.SnapshotCodec
import app.hexavore.domain.backup.SnapshotRead
import app.hexavore.domain.backup.SnapshotStore
import app.hexavore.domain.backup.StoredPreferences
import app.hexavore.domain.profile.WeightEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate

/**
 * La sauvegarde vue du domaine : la rotation, la copie de sécurité, et l'effacement.
 *
 * Ce qui se juge ici est **l'ordre des gestes**, pas le format. Une rotation qui
 * supprime avant d'écrire, une copie de sécurité prise pour un fichier qu'on va
 * refuser, un effacement qui oublie les secrets : trois fautes qui ne se voient pas à
 * la lecture et qui coûtent des données.
 */
class BackupUseCasesTest {
    private val store = FakeSnapshotStore()
    private val codec = FakeCodec()
    private val cible = InMemoryBackupTarget()
    private val securite = InMemoryBackupTarget()

    // --- Ecrire ---------------------------------------------------------------------

    @Test
    fun `une sauvegarde ecrit un fichier`() = runTest {
        val ecrit = createBackup()(cible)

        assertTrue(ecrit.isSuccess)
        assertEquals(1, cible.names.size)
    }

    @Test
    fun `la rotation garde les cinq plus recents`() = runTest {
        repeat(7) { jour -> createBackup(LUNDI.plusDays(jour.toLong()))(cible) }

        assertEquals(BACKUP_ROTATION, cible.names.size)
        assertEquals(LUNDI.plusDays(6), cible.list().first().createdAt.jour())
        assertEquals(LUNDI.plusDays(2), cible.list().last().createdAt.jour())
    }

    @Test
    fun `la copie de securite n en garde qu une`() = runTest {
        repeat(3) { jour -> createBackup(LUNDI.plusDays(jour.toLong()))(securite, keep = 1) }

        assertEquals(1, securite.names.size)
        assertEquals(LUNDI.plusDays(2), securite.list().single().createdAt.jour())
    }

    @Test
    fun `une cible qui refuse ne fait pas tomber l appelant`() = runTest {
        cible.failing = true

        assertTrue(createBackup()(cible).isFailure, "une cible pleine ou absente est un resultat, pas un plantage")
    }

    @Test
    fun `une ecriture qui echoue ne supprime rien`() = runTest {
        // La rotation vient apres l'ecriture, jamais avant : supprimer d'abord et
        // echouer ensuite retirerait une copie saine sans en produire de neuve.
        repeat(BACKUP_ROTATION) { jour -> createBackup(LUNDI.plusDays(jour.toLong()))(cible) }
        cible.failing = true

        createBackup(LUNDI.plusDays(9))(cible)

        assertEquals(BACKUP_ROTATION, cible.names.size)
    }

    // --- Restaurer ---------------------------------------------------------------------

    @Test
    fun `restaurer remplace tout le contenu`() = runTest {
        val resultat = restoreArchive()(FICHIER.inputStream())

        assertEquals(RestoreOutcome.Restored(0), resultat)
        assertEquals(RESTAURE, store.content)
    }

    @Test
    fun `restaurer prend une copie de securite avant d ecraser`() = runTest {
        restoreArchive()(FICHIER.inputStream())

        assertEquals(1, securite.names.size)
    }

    @Test
    fun `un fichier trop recent ne touche a rien`() = runTest {
        codec.read = SnapshotRead.TooRecent(99)

        val resultat = restoreArchive()(FICHIER.inputStream())

        assertEquals(RestoreOutcome.TooRecent(99), resultat)
        assertNull(store.content, "rien n'a ete ecrit")
        assertTrue(securite.names.isEmpty(), "et la copie de securite precedente n'a pas ete consommee")
    }

    @Test
    fun `un fichier illisible ne touche a rien`() = runTest {
        codec.read = SnapshotRead.Unreadable

        assertEquals(RestoreOutcome.Unreadable, restoreArchive()(FICHIER.inputStream()))
        assertNull(store.content)
        assertTrue(securite.names.isEmpty())
    }

    @Test
    fun `une ecriture qui echoue laisse la copie de securite`() = runTest {
        store.failing = true

        assertEquals(RestoreOutcome.Failed, restoreArchive()(FICHIER.inputStream()))
        assertEquals(1, securite.names.size, "c'est elle qui permet de revenir en arriere")
    }

    // --- Effacer ---------------------------------------------------------------------

    @Test
    fun `effacer emporte le contenu et les reglages`() = runTest {
        // **Les deux rangements, et le cas le dit.** Vider la base laisserait derriere
        // elle la cle d'API, le compte Open Food Facts et l'etat de l'adaptation --
        // c'est-a-dire tout ce qui permet de reconnaitre l'utilisateur.
        val reglages = FakeStoredPreferences()
        val photos = InMemoryDishPhotos()
        store.content = RESTAURE

        EraseEverything(store, reglages, photos)()

        assertNull(store.content)
        assertTrue(reglages.erased, "les reglages et les cles partent avec le journal")
        assertTrue(photos.photos.isEmpty(), "les photos sont le troisieme rangement, et elles partent aussi")
    }

    @Test
    fun `effacer ne touche pas aux sauvegardes`() = runTest {
        // Ce sont des fichiers ranges ailleurs -- un Drive, une cle. Les supprimer
        // sans le demander detruirait la seule chose qui restait.
        createBackup()(cible)

        EraseEverything(store, FakeStoredPreferences(), InMemoryDishPhotos())()

        assertEquals(1, cible.names.size)
    }

    // --- Exporter --------------------------------------------------------------------

    @Test
    fun `exporter encode ce qui vient d etre capture`() = runTest {
        store.content = RESTAURE

        val octets = ExportBackup(store, codec)()

        assertEquals(RESTAURE, codec.encoded, "les octets doivent decrire l'instant de la capture")
        assertEquals(FICHIER.decodeToString(), octets.decodeToString(), "et c'est le codec qui les produit")
    }

    @Test
    fun `exporter ne touche a rien`() = runTest {
        // Le cas qui distingue un export d'une sauvegarde : celui-ci ne range nulle
        // part, il rend des octets. Rien ne doit apparaitre dans une cible.
        store.content = RESTAURE

        ExportBackup(store, codec)()

        assertTrue(cible.names.isEmpty(), "un export par fichier n'ecrit dans aucune cible")
    }

    private fun createBackup(jour: LocalDate = LUNDI) =
        CreateBackup(ExportBackup(store, codec), FixedClock.atNoon(jour))

    private fun restoreArchive() = RestoreArchive(store, FakeArchive(codec), createBackup(), securite)

    /**
     * Une archive réduite à ce qu'elle a de commun avec un fichier de journal seul.
     *
     * Ce que le zip ajoute — les photos, l'ordre des entrées, le repli sur l'ancien
     * format — est éprouvé dans `:data:backup`, sur de vrais octets. Ici, ce qui est en
     * jeu est la règle du cas d'usage : quand la copie de sécurité part, et ce qui reste
     * intact quand la lecture est refusée.
     */
    private class FakeArchive(private val codec: SnapshotCodec) : SnapshotArchive {
        override suspend fun write(snapshot: Snapshot, sink: OutputStream): Long {
            val bytes = codec.encode(snapshot)
            sink.write(bytes)
            return bytes.size.toLong()
        }

        override suspend fun read(source: InputStream): SnapshotRead = codec.decode(source.readBytes())
    }

    /** Le second rangement, reduit a la seule question que le cas d'usage lui pose. */
    private class FakeStoredPreferences : StoredPreferences {
        var erased = false

        override suspend fun erase() {
            erased = true
        }
    }

    private fun Instant.jour(): LocalDate = atZone(java.time.ZoneId.of("Europe/Paris")).toLocalDate()

    /** Un magasin qui retient ce qu'on lui écrit, et qui sait refuser. */
    private class FakeSnapshotStore(var failing: Boolean = false) : SnapshotStore {
        var content: Snapshot? = null

        override suspend fun capture(): Snapshot = content ?: VIDE

        override suspend fun replace(snapshot: Snapshot) {
            check(!failing) { "l'ecriture a echoue" }
            content = snapshot
        }

        override suspend fun erase() {
            content = null
        }
    }

    /** Un codec qui ne code rien : ce qui se juge ici est l'ordre des gestes. */
    /**
     * Retient **ce qu'on lui a donne a encoder**, et pas seulement ce qu'il a rendu.
     *
     * Sans cela, comparer les octets d'un export a `codec.encode(attendu)` comparerait
     * `FICHIER` a `FICHIER` : le cas passerait meme si le cas d'usage encodait un
     * instantane vide. Un faux qui rend toujours la meme chose ne peut affirmer que
     * ce qu'on lui a demande.
     */
    private class FakeCodec(var read: SnapshotRead = SnapshotRead.Readable(RESTAURE)) : SnapshotCodec {
        var encoded: Snapshot? = null

        override suspend fun encode(snapshot: Snapshot): ByteArray {
            encoded = snapshot
            return FICHIER
        }

        override suspend fun decode(bytes: ByteArray): SnapshotRead = read
    }

    private companion object {
        val LUNDI: LocalDate = LocalDate.of(2026, 8, 17)
        val MAINTENANT: Instant = Instant.parse("2026-08-17T10:00:00Z")
        val FICHIER = "peu importe".toByteArray()

        val VIDE = Snapshot(exportedAt = MAINTENANT, appVersion = "0.4")
        val RESTAURE = Snapshot(
            exportedAt = MAINTENANT,
            appVersion = "0.4",
            weights = listOf(WeightEntry(LUNDI, 88.4)),
        )
    }
}
