package app.hexavore.integration.reports

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.report.REPORT_ADDRESS
import app.hexavore.domain.report.Report
import app.hexavore.domain.report.ReportSender
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ouvre l'application de courriel, préremplie.
 *
 * ### Un courriel, et rien d'autre
 *
 * `ACTION_SENDTO` avec une adresse `mailto:` garantit qu'un client de courriel réponde,
 * mais il ne sait pas porter de pièce jointe — or le rapport en porte deux, dont la
 * photo qui est précisément ce qu'on veut voir ([D138][decisions]). `ACTION_SEND` les
 * porte, et l'adresse voyage dans `EXTRA_EMAIL`.
 *
 * **Mais `ACTION_SEND` s'adresse à tout ce qui sait partager.** Le type `message/rfc822`
 * est revendiqué par les messageries, le stockage en ligne, les réseaux : l'appui
 * ouvrait le choisisseur d'Android avec WhatsApp, Discord, Drive et Telegram, dont
 * aucun ne sait écrire à une adresse de courriel ([D154][decisions]).
 *
 * Les deux se combinent par un **sélecteur** : l'intention principale porte la pièce
 * jointe et les extras, le sélecteur porte `ACTION_SENDTO` et le schéma `mailto:`. Le
 * système résout alors sur le sélecteur — donc sur les seules applications qui savent
 * envoyer un courriel — et délivre l'intention principale, avec sa pièce jointe. C'est
 * la méthode documentée, et c'est ce que font les applications qui tombent directement
 * sur le bon client.
 *
 * Le manifeste déclare la même intention en `<queries>` : depuis Android 11, une
 * application ne voit que ce qu'elle a déclaré chercher, et sans cela la résolution se
 * fait dans le noir.
 *
 * ### Les fichiers vivent dans un cache à eux
 *
 * Le fournisseur de ce module ne donne accès qu'à `cache/reports/`. Il ne peut donc
 * remettre à une autre application ni la base du journal, ni les photos des plats, ni
 * quoi que ce soit d'autre — c'est la règle que `:feature:capture` applique déjà à son
 * propre dossier.
 *
 * Le dossier est **vidé avant chaque rapport** : ce qui n'a pas été envoyé hier n'a pas
 * à partir aujourd'hui, et un cache qui grossit d'une photo par signalement finirait
 * par se remarquer.
 *
 * [decisions]: docs/11-decisions.md
 */
@Singleton
class MailReports @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : ReportSender {
    override suspend fun propose(report: Report): Boolean = withContext(dispatchers.io) {
        val attachments = runCatching { write(report) }.getOrDefault(emptyList())

        runCatching { context.startActivity(mailIntent(report, attachments)) }.isSuccess
    }

    /**
     * L'intention qui ouvre un courriel prérempli, **et seulement un courriel**.
     *
     * Sans pièce jointe, `ACTION_SENDTO` sur `mailto:` suffit : il ne désigne qu'un
     * client de courriel. Avec, il faut `ACTION_SEND`, qui porte le fichier mais
     * s'adresse à tout ce qui sait partager — d'où le **sélecteur**, qui ramène la
     * résolution aux seules applications de courriel sans rien retirer à l'intention
     * délivrée ([D154][decisions]).
     *
     * Le sélecteur ne porte **pas** l'adresse : `mailto:` nu décrit la famille
     * d'applications visée, et une adresse y ferait un second destinataire à côté de
     * celui d'`EXTRA_EMAIL`, que certains clients concatènent.
     *
     * [decisions]: docs/11-decisions.md
     */
    internal fun mailIntent(report: Report, attachments: List<Uri>): Intent =
        Intent(if (attachments.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_ADDRESS))
            putExtra(Intent.EXTRA_SUBJECT, report.subject)
            putExtra(Intent.EXTRA_TEXT, report.body)

            if (attachments.isEmpty()) {
                action = Intent.ACTION_SENDTO
                data = Uri.parse(MAILTO + REPORT_ADDRESS)
            } else {
                type = MIME_EMAIL
                if (attachments.size > 1) {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(attachments))
                } else {
                    putExtra(Intent.EXTRA_STREAM, attachments.first())
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                selector = Intent(Intent.ACTION_SENDTO, Uri.parse(MAILTO))
            }

            // Lancee depuis un contexte d'application : sans ce drapeau, le systeme
            // refuse d'ouvrir une activite qui n'appartient a aucune pile.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Écrit les pièces jointes dans le cache du module, et rend leurs adresses. */
    private fun write(report: Report): List<Uri> {
        val folder = File(context.cacheDir, DIRECTORY).apply {
            deleteRecursively()
            mkdirs()
        }
        val authority = context.packageName + PROVIDER_SUFFIX

        return report.attachments.map { attachment ->
            val file = File(folder, attachment.name).apply { writeBytes(attachment.bytes) }
            FileProvider.getUriForFile(context, authority, file)
        }
    }

    private companion object {
        const val DIRECTORY = "reports"

        /** L'autorite derive de l'applicationId : deux variantes cote a cote ne se disputent rien. */
        const val PROVIDER_SUFFIX = ".reports"

        /** Le type qui designe un courriel, et que les clients declarent. */
        const val MIME_EMAIL = "message/rfc822"

        /** Le schema qui ne designe que les clients de courriel. */
        const val MAILTO = "mailto:"
    }
}
