package app.hexavore.core.testing

import app.hexavore.domain.report.Report
import app.hexavore.domain.report.ReportSender

/**
 * Un destinataire de rapports qui garde ce qu'on lui propose.
 *
 * **Le vrai ouvre une application de messagerie** ([D138][decisions]), donc un test qui
 * veut savoir *ce qui serait parti* n'a rien à observer sur l'appareil : il l'observe
 * ici.
 *
 * @param accepts ce que [propose] retourne. `false` simule un téléphone sans
 *   application de courriel — le cas où le bouton ne doit pas mentir sur son
 *   résultat, tout en restant silencieux à l'écran.
 * @see docs/11-decisions.md
 *
 * [decisions]: docs/11-decisions.md
 */
class RecordedReports(private val accepts: Boolean = true) : ReportSender {
    private val sent = mutableListOf<Report>()

    /** Les rapports proposés, dans l'ordre. */
    val proposed: List<Report> get() = sent.toList()

    /** Le dernier, qui est ce que l'écran vient de demander. */
    val last: Report? get() = sent.lastOrNull()

    override suspend fun propose(report: Report): Boolean {
        sent += report
        return accepts
    }
}
