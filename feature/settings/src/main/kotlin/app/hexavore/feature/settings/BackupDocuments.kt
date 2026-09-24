package app.hexavore.feature.settings

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

/**
 * Le passage entre un document choisi par l'utilisateur et des octets.
 *
 * **Le Storage Access Framework, et aucune permission de stockage.** L'utilisateur
 * désigne lui-même un document — stockage local, Nextcloud, clé USB, peu importe — et
 * l'application n'obtient l'accès qu'à celui-là ([docs/09][donnees]). C'est aussi
 * pourquoi ce chemin n'est **pas** un `BackupTarget` : il n'y a rien à lister et rien à
 * faire tourner, seulement un document par geste.
 *
 * Des fonctions de fichier plutôt qu'une classe injectée, comme `PhotoFiles` : ce qui
 * traverse ensuite est un tableau d'octets, et rien ici ne mérite d'être un port du
 * domaine.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
internal fun readDocument(context: Context, uri: Uri): InputStream? =
    runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()

/**
 * Ouvre le document en écriture, ou rend `null`.
 *
 * **`truncate` et non `write`.** Un document réutilisé peut être plus grand que ce
 * qu'on y écrit, et le mode par défaut ne raccourcit pas : la queue de l'ancien fichier
 * survivrait derrière la nouvelle archive, produisant une sauvegarde illisible que rien
 * n'aurait signalée avant le jour où l'on en a besoin.
 *
 * **Un flux et non des octets**, depuis que l'archive emporte les photos : une année
 * d'images ne tient pas en mémoire, et ce qui la traverse doit donc être le document
 * lui-même.
 */
internal fun writeDocument(context: Context, uri: Uri): OutputStream? =
    runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()

/**
 * Le nom proposé au sélecteur de document.
 *
 * La date et non l'horodatage complet : ce nom sert à reconnaître un fichier dans une
 * liste, et deux exports du même jour se distinguent par ce que le système ajoute
 * lui-même : « (1) », « (2) ». Une heure à la seconde ne rendrait pas le choix plus
 * facile, elle rendrait le nom plus long.
 *
 * **`.zip` depuis que les photos voyagent avec le journal.** Un ancien `.json.gz` reste
 * lisible à l'import, et le journal garde son nom **à l'intérieur** de l'archive : on
 * l'en sort et on le répare à la main comme avant.
 */
internal fun backupFileName(today: LocalDate): String = "hexavore-$today.zip"
