package app.hexavore.domain.usecase

import app.hexavore.domain.backup.BACKUP_ROTATION
import app.hexavore.domain.backup.BackupFile
import app.hexavore.domain.backup.BackupTarget
import app.hexavore.domain.backup.Snapshot
import app.hexavore.domain.backup.SnapshotArchive
import app.hexavore.domain.backup.SnapshotCodec
import app.hexavore.domain.backup.SnapshotRead
import app.hexavore.domain.backup.SnapshotStore
import app.hexavore.domain.backup.StoredPreferences
import app.hexavore.domain.diary.DishPhotos
import app.hexavore.domain.time.Clock
import java.io.InputStream
import java.io.OutputStream

/**
 * Tout ce que l'utilisateur a écrit, en octets.
 *
 * **La seule capture-puis-encodage du projet.** [CreateBackup] s'en sert pour remplir
 * une cible, l'export par fichier s'en sert pour remplir un document que l'utilisateur
 * a choisi — et le Storage Access Framework n'est pas une cible, parce qu'on n'y liste
 * rien et qu'on n'y fait rien tourner. Deux chemins, une seule règle.
 *
 * L'ordre n'est pas interchangeable : encoder ne relit rien, donc les octets rendus
 * décrivent l'instant de la capture et pas un autre. C'est ce qui permet à l'écran
 * d'écrire le fichier plus tard, quand l'utilisateur a fini de choisir un dossier,
 * sans que le contenu ait bougé sous lui.
 */
class ExportBackup(private val store: SnapshotStore, private val codec: SnapshotCodec) {
    suspend operator fun invoke(): ByteArray = codec.encode(store.capture())
}

/**
 * Écrire un instantané dans une cible, et n'y garder que les plus récents.
 *
 * **La rotation vit ici et non dans la cible**, parce que ce n'est pas une propriété du
 * rangement : c'est le nombre de retours en arrière qu'on veut se garder. La copie de
 * sécurité d'avant restauration n'en veut qu'un, Drive en veut cinq, et les deux
 * s'écrivent au même endroit.
 */
class CreateBackup(private val export: ExportBackup, private val clock: Clock) {
    /**
     * @param keep le nombre de fichiers conservés, le plus ancien partant d'abord.
     * @return le fichier écrit, ou l'échec — une cible peut être pleine, absente ou
     *   refusée, et l'appelant doit pouvoir le dire.
     */
    suspend operator fun invoke(target: BackupTarget, keep: Int = BACKUP_ROTATION): Result<BackupFile> = runCatching {
        val written = target.write(export(), clock.now())
        // La rotation apres l'ecriture, jamais avant : supprimer d'abord et echouer
        // ensuite retirerait une copie saine sans en produire de neuve.
        target.list().drop(keep).forEach { target.delete(it.id) }
        written
    }
}

/**
 * L'archive complète, écrite dans le document que l'utilisateur a choisi.
 *
 * **Elle ne passe pas par la mémoire.** [ExportBackup] rend des octets et c'est très
 * bien pour un journal ; une année de photos pèse deux cents mégaoctets, et les tenir
 * d'un bout à l'autre ferait tomber l'application chez ceux qui ont le plus à
 * sauvegarder.
 *
 * **La capture précède l'ouverture du document**, et c'est pour cela que ce cas d'usage
 * rend un écrivain plutôt que d'écrire lui-même : ce qui part décrit l'instant où
 * l'export a été demandé, et non celui où le fichier a fini de s'ouvrir. La différence
 * est invisible en démonstration, réelle si une saisie arrive entre les deux.
 *
 * Les photos, elles, sont lues à l'écriture. Une image prise entre les deux instants
 * entre donc dans l'archive sans que son plat y soit : elle en ressort orpheline à la
 * restauration, et le balayage du démarrage l'emporte.
 */
class ExportArchive(private val store: SnapshotStore, private val archive: SnapshotArchive) {
    suspend operator fun invoke(): PendingExport = PendingExport(store.capture(), archive)
}

/** Ce qui partira, figé, en attendant un document où l'écrire. */
class PendingExport internal constructor(private val snapshot: Snapshot, private val archive: SnapshotArchive) {
    /** @return les octets écrits, pour que l'écran puisse le dire. */
    suspend fun writeTo(sink: OutputStream): Long = archive.write(snapshot, sink)
}

/**
 * Remplacer tout le contenu de l'application par celui d'une archive.
 *
 * **Une copie de sécurité part d'abord**, et elle n'en garde qu'une
 * ([docs/09][donnees]) : c'est ce qui permet de revenir en arrière quand quelqu'un
 * restaure le mauvais fichier. Elle est écrite **après** la lecture de l'archive, parce
 * que sauvegarder pour un import qui va être refusé ferait perdre la copie précédente
 * sans rien restaurer.
 *
 * **Elle ne porte que le journal**, là où l'archive porte aussi les photos : elle vit
 * sur le même disque qu'elles, et la doubler mettrait deux fois leur poids sur un
 * téléphone dont on ne sait rien. Revenir en arrière rend donc le journal, et les
 * images sont encore là — rien ne les efface avant le balayage du prochain démarrage.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
class RestoreArchive(
    private val store: SnapshotStore,
    private val archive: SnapshotArchive,
    private val createBackup: CreateBackup,
    private val safety: BackupTarget,
) {
    suspend operator fun invoke(source: InputStream): RestoreOutcome =
        when (val read = runCatching { archive.read(source) }.getOrDefault(SnapshotRead.Unreadable)) {
            is SnapshotRead.Readable -> replace(read)
            is SnapshotRead.TooRecent -> RestoreOutcome.TooRecent(read.formatVersion)
            SnapshotRead.Unreadable -> RestoreOutcome.Unreadable
        }

    private suspend fun replace(read: SnapshotRead.Readable): RestoreOutcome = runCatching {
        createBackup(safety, keep = 1)
        store.replace(read.snapshot)
        RestoreOutcome.Restored(read.snapshot.entryCount)
    }.getOrElse { RestoreOutcome.Failed }
}

/** Ce qu'une restauration a donné. */
sealed interface RestoreOutcome {
    data class Restored(val entryCount: Int) : RestoreOutcome

    /** Le fichier vient d'une version plus récente. Rien n'a été touché. */
    data class TooRecent(val formatVersion: Int) : RestoreOutcome

    /** Ni du JSON, ni du gzip, ni la bonne forme. Rien n'a été touché. */
    data object Unreadable : RestoreOutcome

    /** L'écriture a échoué. La copie de sécurité, elle, est là. */
    data object Failed : RestoreOutcome
}

/**
 * Tout effacer : le journal, le profil, les objectifs, les clés, les comptes.
 *
 * **Les secrets partent avec le reste.** [docs/09][donnees] le veut ainsi : quelqu'un
 * qui efface ses données ne doit pas retrouver sa clé d'API et son compte Open Food
 * Facts au prochain lancement.
 *
 * **Trois dépendances et non dix**, et c'est une correction. Ce cas d'usage oubliait
 * jusqu'ici trois réglages : l'adaptation hebdomadaire, le consentement photo, le
 * compteur d'appels. Il composait des oublis un à un, et une liste écrite ici se tait
 * quand on omet de l'allonger. Elle est désormais tenue par celui qui range
 * ([StoredPreferences][app.hexavore.domain.backup.StoredPreferences]).
 *
 * **Les photos sont la troisième**, et elles y sont parce qu'elles sont le troisième
 * endroit où vivent des données : la base, les préférences, le disque. Le réglage qui
 * dit s'il faut en garder, lui, reste ; c'est une préférence d'appareil, et quelqu'un
 * qui repart de zéro la retrouvera telle qu'il l'avait posée, comme son thème.
 *
 * **Le journal d'abord, les réglages ensuite.** Si le second geste échoue, il reste des
 * préférences sans journal — l'état d'une installation neuve à qui l'on aurait déjà
 * donné une clé, que l'application sait afficher. L'ordre inverse laisserait un journal
 * sans profil, que rien n'est prêt à lire.
 *
 * Les sauvegardes ne sont pas effacées ici. Ce sont des fichiers que l'utilisateur a
 * rangés ailleurs — dans son Drive, sur une clé — et les supprimer sans le lui demander
 * détruirait la seule chose qui restait.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
class EraseEverything(
    private val store: SnapshotStore,
    private val preferences: StoredPreferences,
    private val photos: DishPhotos,
) {
    suspend operator fun invoke() {
        store.erase()
        photos.forgetAll()
        preferences.erase()
    }
}
