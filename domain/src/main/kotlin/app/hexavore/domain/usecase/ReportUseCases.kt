package app.hexavore.domain.usecase

import app.hexavore.domain.ai.AiExchange
import app.hexavore.domain.report.Attachment
import app.hexavore.domain.report.CrashReports
import app.hexavore.domain.report.Report
import app.hexavore.domain.report.ReportSender

/**
 * Propose d'envoyer la trace du dernier plantage, s'il y en a eu un.
 *
 * **Et l'oublie dans tous les cas**, que le courriel parte ou non ([D138][decisions]).
 * Une proposition qu'on décline et qui revient à chaque lancement devient une punition
 * pour un plantage dont on n'est pas l'auteur — et l'on finirait par ne plus lire ce
 * qu'elle propose.
 *
 * [decisions]: docs/11-decisions.md
 */
class ReportCrash(private val crashes: CrashReports, private val sender: ReportSender) {
    /** @return `true` si une trace attendait et qu'un courriel a pu s'ouvrir. */
    suspend operator fun invoke(subject: String, intro: String): Boolean {
        val trace = crashes.pending() ?: return false
        crashes.clear()

        return sender.propose(
            Report(
                subject = subject,
                // La trace **dans le corps** et non en piece jointe : elle tient en
                // quelques lignes, et ce qu'on lit avant d'envoyer est ce qu'on accepte
                // d'envoyer. Une piece jointe se transmet sans avoir ete ouverte.
                body = intro + "\n\n" + trace,
            ),
        )
    }
}

/**
 * Propose de signaler une proposition d'IA incorrecte.
 *
 * ### Ce que le rapport porte
 *
 * **L'échange entier et la photo**, c'est-à-dire exactement ce qu'il faut pour
 * comprendre — et exactement ce que le mode debug montre déjà à l'écran ([D108][decisions]).
 * Sans la requête, on ne sait pas ce qui a été demandé ; sans la réponse, on ne sait pas
 * ce qui a été rendu ; sans la photo, on ne sait pas ce qu'il y avait dans l'assiette.
 *
 * ### Et ce qu'il ne porte pas
 *
 * **La clé d'API n'y est pas** : l'intercepteur de rédaction l'a retirée de l'échange
 * avant qu'il soit noté, et c'est la même règle qui protège la sauvegarde
 * ([01][perimetre]). Le journal alimentaire n'y est pas non plus — un signalement parle
 * d'un plat, pas de ce qu'on a mangé cette année.
 *
 * [perimetre]: docs/01-perimetre.md
 * [decisions]: docs/11-decisions.md
 */
class ReportAnalysis(private val sender: ReportSender) {
    /**
     * @param exchange le dernier échange, ou `null` quand le mode debug ne les notait
     *   pas : le rapport part quand même, avec la photo et ce que l'écran affiche. Un
     *   signalement amputé vaut mieux qu'un bouton qui ne fait rien.
     */
    suspend operator fun invoke(
        subject: String,
        description: String,
        exchange: AiExchange?,
        photo: ByteArray?,
    ): Boolean {
        val attachments = buildList {
            exchange?.let {
                add(Attachment(name = "echange.txt", mimeType = "text/plain", bytes = it.transcript()))
            }
            photo?.let { add(Attachment(name = "assiette.jpg", mimeType = "image/jpeg", bytes = it)) }
        }

        return sender.propose(Report(subject = subject, body = description, attachments = attachments))
    }
}

/**
 * L'échange, mis à plat pour être lu.
 *
 * La même chose que le mode debug affiche à l'écran ([D108][decisions]), avec ses deux
 * moitiés séparées : sans la requête on ne sait pas ce qui a été demandé, sans la
 * réponse on ne sait pas ce qui a été rendu.
 *
 * [decisions]: docs/11-decisions.md
 */
private fun AiExchange.transcript(): ByteArray = buildString {
    appendLine(endpoint)
    appendLine("HTTP $status")
    appendLine()
    appendLine("--- requete ---")
    appendLine(request)
    appendLine()
    appendLine("--- reponse ---")
    append(response)
}.toByteArray()
