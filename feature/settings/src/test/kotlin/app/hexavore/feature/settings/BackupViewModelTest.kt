package app.hexavore.feature.settings

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryBackupTarget
import app.hexavore.core.testing.InMemoryDishPhotos
import app.hexavore.domain.backup.Snapshot
import app.hexavore.domain.backup.SnapshotArchive
import app.hexavore.domain.backup.SnapshotCodec
import app.hexavore.domain.backup.SnapshotRead
import app.hexavore.domain.backup.SnapshotStore
import app.hexavore.domain.backup.StoredPreferences
import app.hexavore.domain.usecase.CreateBackup
import app.hexavore.domain.usecase.EraseEverything
import app.hexavore.domain.usecase.ExportArchive
import app.hexavore.domain.usecase.ExportBackup
import app.hexavore.domain.usecase.RestoreArchive
import app.hexavore.domain.usecase.UndoRestore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate

/**
 * L'écran de sauvegarde : ce qu'il produit, ce qu'il rapporte, ce qu'il refuse.
 *
 * **L'export en deux temps est la règle qui casserait en silence.** Les octets sont
 * produits *avant* que le sélecteur de document s'ouvre ; l'ordre inverse écrirait
 * l'état de l'application au moment où l'utilisateur a fini de parcourir ses dossiers.
 * La différence est invisible en démonstration, réelle si une saisie arrive entre les
 * deux — donc invisible aussi pour un test qui ne la cherche pas.
 *
 * Ce qui n'est **pas** éprouvé ici : les dialogues de confirmation, le sélecteur de
 * documents, et la mise en mots des comptes rendus — trois choses d'écran. Le mot du
 * verrou, lui, se teste : voir `BackupWordTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BackupViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = FakeSnapshotStore()
    private val codec = FakeCodec()
    private val preferences = FakeStoredPreferences()

    /** L'archive, reduite a ce qu'elle a de commun avec un journal seul : voir BackupUseCasesTest. */
    private val archive = object : SnapshotArchive {
        override suspend fun write(snapshot: Snapshot, sink: OutputStream): Long {
            val bytes = codec.encode(snapshot)
            sink.write(bytes)
            return bytes.size.toLong()
        }

        override suspend fun read(source: InputStream): SnapshotRead = codec.decode(source.readBytes())
    }

    /** La copie de sécurité d'avant restauration : celle qu'écrit la restauration et que relit le retour. */
    private val securite = InMemoryBackupTarget()

    private val viewModel by lazy {
        val createBackup = CreateBackup(ExportBackup(store, codec), horloge)
        BackupViewModel(
            exportArchive = ExportArchive(store, archive),
            restoreArchive = RestoreArchive(store, archive, createBackup, securite),
            undoRestore = UndoRestore(store, codec, createBackup, securite),
            eraseEverything = EraseEverything(store, preferences, InMemoryDishPhotos(), securite),
            clock = horloge,
        )
    }

    @BeforeEach
    fun installer() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun retirer() = Dispatchers.resetMain()

    @Test
    fun `exporter capture avant que le document soit choisi`() = runTest {
        // **La regle de l'ordre.** On simule une saisie arrivee pendant que
        // l'utilisateur parcourait ses dossiers : ce qui est ecrit doit decrire
        // l'instant de la demande, pas celui du choix.
        store.content = PLEIN

        viewModel.onExport {
            store.content = AUTRE
            ByteArrayOutputStream()
        }

        assertEquals(PLEIN, codec.encoded, "les octets decrivent l'instant de la demande")
    }

    @Test
    fun `un export ecrit rapporte sa taille`() = runTest {
        viewModel.onExport { ByteArrayOutputStream() }

        assertEquals(BackupMessage.Exported(FICHIER.size.toLong()), viewModel.uiState.value.message)
    }

    @Test
    fun `un document qui refuse l ecriture se dit`() = runTest {
        // Plein, retire, ou refuse par son fournisseur : sans message, l'utilisateur
        // croit avoir une sauvegarde.
        viewModel.onExport { null }

        assertEquals(BackupMessage.ExportFailed, viewModel.uiState.value.message)
    }

    @Test
    fun `un document illisible se dit sans rien toucher`() = runTest {
        store.content = PLEIN

        viewModel.onImport { null }

        assertEquals(BackupMessage.Unreadable, viewModel.uiState.value.message)
        assertEquals(PLEIN, store.content, "rien n'a ete remplace")
    }

    @Test
    fun `un fichier trop recent se dit avec sa version`() = runTest {
        // Le message porte le numero : « ca n'a pas marche » ne dit pas s'il faut
        // chercher un autre fichier ou mettre l'application a jour.
        codec.read = SnapshotRead.TooRecent(formatVersion = 9)

        viewModel.onImport { FICHIER.inputStream() }

        assertEquals(BackupMessage.TooRecent(9), viewModel.uiState.value.message)
    }

    @Test
    fun `une restauration remplace le contenu et le rapporte`() = runTest {
        store.content = AUTRE
        codec.read = SnapshotRead.Readable(PLEIN)

        viewModel.onImport { FICHIER.inputStream() }

        assertEquals(PLEIN, store.content)
        assertEquals(BackupMessage.Restored(PLEIN.entryCount), viewModel.uiState.value.message)
    }

    // --- Revenir en arriere (D162) ----------------------------------------------------

    @Test
    fun `sans restauration, rien ne se propose a defaire`() = runTest {
        assertNull(viewModel.uiState.value.undoSince)
    }

    @Test
    fun `une restauration propose ensuite de revenir en arriere, datee`() = runTest {
        // La boite de restauration le promettait, et aucun ecran ne l'offrait.
        codec.read = SnapshotRead.Readable(PLEIN)

        viewModel.onImport { FICHIER.inputStream() }

        assertEquals(JOUR.atTime(12, 0), viewModel.uiState.value.undoSince, "a l'heure de l'horloge du domaine")
    }

    @Test
    fun `revenir en arriere remet l etat d avant et le rapporte`() = runTest {
        store.content = AUTRE
        codec.read = SnapshotRead.Readable(PLEIN)
        viewModel.onImport { FICHIER.inputStream() }
        codec.read = SnapshotRead.Readable(AUTRE)

        viewModel.onUndoRestore()

        assertEquals(AUTRE, store.content)
        assertEquals(BackupMessage.Undone(AUTRE.entryCount), viewModel.uiState.value.message)
    }

    @Test
    fun `effacer retire aussi le retour en arriere`() = runTest {
        // La copie est le journal entier : elle part avec le reste, et la carte avec elle.
        codec.read = SnapshotRead.Readable(PLEIN)
        viewModel.onImport { FICHIER.inputStream() }

        viewModel.onErase()

        assertNull(viewModel.uiState.value.undoSince)
        assertTrue(securite.names.isEmpty())
    }

    @Test
    fun `effacer emporte le contenu et les reglages`() = runTest {
        store.content = PLEIN

        viewModel.onErase()

        assertNull(store.content)
        assertTrue(preferences.erasures > 0, "les reglages et les cles partent avec le journal")
        assertEquals(BackupMessage.Erased, viewModel.uiState.value.message)
    }

    @Test
    fun `le compte rendu ne revient pas une fois lu`() = runTest {
        // Sans cela, la barre reapparaitrait a chaque rotation de l'ecran et
        // annoncerait un effacement qui date de la veille.
        viewModel.onErase()

        viewModel.onMessageShown()

        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `l ecran ne travaille pas deux fois a la fois`() = runTest {
        // Les trois gestes touchent la meme base : un effacement lance au milieu d'une
        // restauration laisserait un journal a moitie ecrit.
        var exports = 0
        store.retenu = CompletableDeferred()

        viewModel.onExport {
            exports++
            ByteArrayOutputStream()
        }
        viewModel.onExport {
            exports++
            ByteArrayOutputStream()
        }

        assertEquals(0, exports, "le premier n'a pas encore rendu la main")
        assertTrue(viewModel.uiState.value.busy)
        store.retenu?.complete(Unit)
        assertEquals(1, exports, "le second geste a ete refuse, pas mis en attente")
    }

    @Test
    fun `un travail fini rouvre l ecran aux suivants`() = runTest {
        viewModel.onErase()

        viewModel.onErase()

        assertFalse(viewModel.uiState.value.busy)
        assertEquals(2, preferences.erasures, "le second effacement passe")
    }

    private val horloge = FixedClock.atNoon(JOUR)

    /** Retient **ce qu'on lui a donné à encoder** : c'est la seule façon d'éprouver l'ordre. */
    private class FakeCodec(var read: SnapshotRead = SnapshotRead.Readable(PLEIN)) : SnapshotCodec {
        var encoded: Snapshot? = null

        override suspend fun encode(snapshot: Snapshot): ByteArray {
            encoded = snapshot
            return FICHIER
        }

        override suspend fun decode(bytes: ByteArray): SnapshotRead = read
    }

    private class FakeSnapshotStore : SnapshotStore {
        var content: Snapshot? = null

        /** Quand il est là, la capture attend : c'est ce qui éprouve le verrou. */
        var retenu: CompletableDeferred<Unit>? = null

        override suspend fun capture(): Snapshot {
            retenu?.await()
            return content ?: VIDE
        }

        override suspend fun replace(snapshot: Snapshot) {
            content = snapshot
        }

        override suspend fun erase() {
            content = null
        }
    }

    private class FakeStoredPreferences : StoredPreferences {
        var erasures = 0

        override suspend fun erase() {
            erasures++
        }
    }

    private companion object {
        val JOUR: LocalDate = LocalDate.of(2026, 8, 24)
        val MAINTENANT: Instant = Instant.parse("2026-08-24T10:00:00Z")
        val FICHIER = "peu importe".toByteArray()

        // Trois instantanes **distincts**, et c'est necessaire : deux `Snapshot` aux
        // memes champs sont egaux, et un cas qui comparerait deux vides passerait
        // meme en capturant le mauvais.
        val VIDE = Snapshot(exportedAt = MAINTENANT, appVersion = "vide")
        val PLEIN = Snapshot(exportedAt = MAINTENANT, appVersion = "plein")
        val AUTRE = Snapshot(exportedAt = MAINTENANT.plusSeconds(60), appVersion = "autre")
    }
}
