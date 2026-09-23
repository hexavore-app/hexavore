package app.hexavore.domain.backup

import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

/**
 * Ce que l'application sait d'elle-même, et comment on le remplace.
 *
 * **Le remplacement est complet, jamais une fusion** ([docs/09][donnees]). Fusionner
 * demanderait une résolution de conflits par entité — même identifiant, contenus
 * différents, dates proches — qui produit des données corrompues en silence. Tant
 * qu'il n'y a pas de scénario multi-appareils réel à servir, la complexité n'est pas
 * justifiée.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
interface SnapshotStore {
    /** Tout ce que l'utilisateur a écrit, maintenant. */
    suspend fun capture(): Snapshot

    /**
     * Vide tout, puis écrit [snapshot]. **En une transaction.**
     *
     * Entre les deux, la base serait vide : une lecture concurrente y verrait un
     * journal effacé, et une interruption y laisserait l'application sans rien.
     */
    suspend fun replace(snapshot: Snapshot)

    /**
     * Vide tout, et n'écrit rien.
     *
     * Le bouton « Effacer toutes mes données » de [docs/09][donnees]. Il ne touche pas
     * aux secrets — ce n'est pas ce port qui les range.
     *
     * [donnees]: docs/09-donnees-et-sauvegarde.md
     */
    suspend fun erase()
}

/**
 * Le passage entre un [Snapshot] et les octets d'un fichier.
 *
 * Un port et non une fonction du domaine : le format est du JSON compressé, et le
 * domaine n'a pas à connaître l'un ni l'autre. Ce qu'il connaît est la **règle** —
 * [SNAPSHOT_FORMAT_VERSION], la chaîne de migrations, et le refus d'un fichier plus
 * récent que l'application.
 */
interface SnapshotCodec {
    suspend fun encode(snapshot: Snapshot): ByteArray

    /** Ne lance pas : un fichier illisible est un résultat, pas un accident. */
    suspend fun decode(bytes: ByteArray): SnapshotRead
}

/**
 * L'archive que l'utilisateur exporte et réimporte : le journal **et** les photos.
 *
 * **Des flux et non des tableaux d'octets**, contrairement à [SnapshotCodec]. Un an de
 * journal tient sous les cent kilo-octets ; un an de photos pèse deux cents
 * mégaoctets, et les porter en mémoire d'un bout à l'autre ferait tomber l'application
 * précisément chez ceux qui ont le plus à sauvegarder. `java.io` n'est pas une
 * dépendance de plateforme : c'est le vocabulaire minimal pour dire « ça ne tient pas
 * en mémoire ».
 *
 * **Le journal vient en premier dans l'archive**, et ce n'est pas un détail de format :
 * c'est ce qui permet de refuser un fichier trop récent **avant** d'avoir touché à la
 * moindre photo.
 *
 * @see docs/09-donnees-et-sauvegarde.md
 */
interface SnapshotArchive {
    /** @return le nombre d'octets écrits, pour que l'écran puisse le dire. */
    suspend fun write(snapshot: Snapshot, sink: OutputStream): Long

    /**
     * Lit le journal d'une archive, **et range ses photos au passage**.
     *
     * L'effet de bord est assumé : les photos sont écrites pendant la lecture du flux,
     * donc avant que l'appelant ait remplacé la base. Si ce remplacement échoue, elles
     * se retrouvent sans plat à décrire, et le balayage du démarrage les emporte. Le
     * contraire aurait demandé de tenir toute l'archive en mémoire.
     *
     * Rien n'est écrit quand le journal est refusé : il est lu en premier.
     *
     * Accepte aussi un ancien fichier de journal seul, écrit avant que les photos
     * existent. Il ne porte alors aucune image, et c'est exact.
     */
    suspend fun read(source: InputStream): SnapshotRead
}

/** Ce qu'on a réussi à lire d'un fichier. */
sealed interface SnapshotRead {
    data class Readable(val snapshot: Snapshot) : SnapshotRead

    /**
     * Le fichier vient d'une version plus récente de l'application.
     *
     * **Refusé, jamais importé partiellement** ([docs/09][donnees]) : les champs qu'on
     * ne sait pas lire seraient silencieusement perdus, et l'utilisateur croirait avoir
     * restauré.
     *
     * [donnees]: docs/09-donnees-et-sauvegarde.md
     */
    data class TooRecent(val formatVersion: Int) : SnapshotRead

    /** Ni du JSON, ni du gzip, ni la bonne forme. */
    data object Unreadable : SnapshotRead
}

/**
 * Un endroit où des sauvegardes s'empilent, et se remplacent.
 *
 * **Deux implémentations interchangeables**, c'est le critère de fin de la tranche 8 :
 * le stockage interne et Google Drive. Ce que ce port ne couvre **pas** est l'export
 * par le Storage Access Framework — l'utilisateur y désigne un document à chaque fois,
 * il n'y a rien à lister ni à faire tourner, et un port qui rendrait `emptyList()` pour
 * la moitié de ses implémentations ne serait pas un port.
 *
 * @see docs/09-donnees-et-sauvegarde.md
 */
interface BackupTarget {
    /** Les fichiers présents, du plus récent au plus ancien. */
    suspend fun list(): List<BackupFile>

    suspend fun write(bytes: ByteArray, at: Instant): BackupFile

    /** `null` si le fichier a disparu entre la liste et la lecture. */
    suspend fun read(id: BackupFileId): ByteArray?

    suspend fun delete(id: BackupFileId)
}

@JvmInline
value class BackupFileId(val value: String)

/**
 * Un fichier de sauvegarde, tel que l'écran de restauration le présente.
 *
 * Date et taille suffisent à reconnaître le bon sans l'ouvrir — c'est ce que
 * [docs/09][donnees] demande.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
data class BackupFile(val id: BackupFileId, val name: String, val createdAt: Instant, val sizeBytes: Long)

/**
 * Le nombre de sauvegardes conservées dans une cible qui en empile.
 *
 * Cinq et non une : une corruption locale sauvegardée écraserait la seule copie saine.
 * Cinq et non vingt : au-delà, l'utilisateur ne sait plus laquelle choisir.
 */
const val BACKUP_ROTATION = 5
