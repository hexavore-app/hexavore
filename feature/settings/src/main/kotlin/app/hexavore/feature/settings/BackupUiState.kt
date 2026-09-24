package app.hexavore.feature.settings

/**
 * Ce que l'écran de sauvegarde montre.
 *
 * **Un seul travail à la fois** ([busy]) : exporter, importer et effacer touchent tous
 * la même base, et lancer le second pendant le premier produirait un fichier écrit à
 * moitié ou un effacement au milieu d'une restauration. Un booléen suffit — il n'y a
 * pas de scénario où l'on voudrait deux de ces gestes en vol.
 *
 * [message] est le compte rendu du dernier geste, et il n'a pas de forme réutilisable :
 * chaque issue dit une chose différente et se traduit à part. Le porter comme un texte
 * déjà résolu ferait ranger des chaînes dans un `ViewModel`, ce que le projet ne fait
 * nulle part ; il porte donc **ce qui s'est passé**, et l'écran choisit les mots.
 */
internal data class BackupUiState(val busy: Boolean = false, val message: BackupMessage? = null)

/** Ce qu'un geste de sauvegarde a produit, avant d'être mis en mots. */
internal sealed interface BackupMessage {
    /** Le fichier est écrit. La taille est ce qui rassure — un export vide ne l'est pas. */
    data class Exported(val sizeBytes: Long) : BackupMessage

    /** Le document n'a pas pu être écrit : plein, retiré, ou refusé par son fournisseur. */
    data object ExportFailed : BackupMessage

    data class Restored(val entryCount: Int) : BackupMessage

    /**
     * Le fichier vient d'une version plus récente. **Rien n'a été touché**, et le
     * message doit le dire : sans cela, l'utilisateur croit avoir tout perdu.
     */
    data class TooRecent(val formatVersion: Int) : BackupMessage

    /** Ni du gzip, ni du JSON, ni la bonne forme. Rien n'a été touché. */
    data object Unreadable : BackupMessage

    /** La restauration a échoué en cours d'écriture. La copie de sécurité, elle, est là. */
    data object RestoreFailed : BackupMessage

    data object Erased : BackupMessage
}
