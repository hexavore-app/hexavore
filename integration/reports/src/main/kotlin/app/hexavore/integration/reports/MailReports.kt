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
 * ### `ACTION_SEND` et non `mailto:`
 *
 * `ACTION_SENDTO` avec une adresse `mailto:` garantit qu'un client de courriel réponde,
 * mais il ne sait pas porter de pièce jointe — or le rapport d'analyse en porte deux,
 * dont la photo qui est précisément ce qu'on veut voir ([D138][decisions]).
 * `ACTION_SEND` les porte, et l'adresse voyage dans `EXTRA_EMAIL`.
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

        val intent = Intent(if (attachments.isEmpty()) Intent.ACTION_SENDTO else Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_ADDRESS))
            putExtra(Intent.EXTRA_SUBJECT, report.subject)
            putExtra(Intent.EXTRA_TEXT, report.body)
            if (attachments.isEmpty()) {
                // Sans piece jointe, `mailto:` est ce qui designe le plus surement un
                // client de courriel -- `ACTION_SEND` sans type ouvrirait n'importe quoi.
                data = Uri.parse("mailto:$REPORT_ADDRESS")
            } else {
                type = MIME_EMAIL
                putExtra(Intent.EXTRA_STREAM, attachments.first())
                if (attachments.size > 1) {
                    action = Intent.ACTION_SEND_MULTIPLE
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(attachments))
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Lancee depuis un contexte d'application : sans ce drapeau, le systeme
            // refuse d'ouvrir une activite qui n'appartient a aucune pile.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching { context.startActivity(intent) }.isSuccess
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
    }
}
