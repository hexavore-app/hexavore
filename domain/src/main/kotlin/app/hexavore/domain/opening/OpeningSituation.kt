package app.hexavore.domain.opening

import app.hexavore.domain.ai.AiSettings
import app.hexavore.domain.report.CrashReports
import app.hexavore.domain.tour.TourSettings
import kotlinx.coroutines.flow.first

/**
 * Ce que l'ouverture doit savoir de l'application pour choisir son message.
 *
 * @property aiConfigured une clé sert : les astuces de la photo et de l'estimation
 *   s'appliquent, celle de la clé gratuite non.
 * @property tourSeen le tour guidé a eu lieu. Avant lui, rien : c'est lui qui parle.
 * @property crashPending un rapport de plantage attend d'être proposé. Il passe en
 *   premier, seul : deux boîtes l'une sur l'autre en feraient refermer une sans la lire.
 */
data class OpeningSituation(val aiConfigured: Boolean, val tourSeen: Boolean, val crashPending: Boolean) {
    /** Rien d'autre ne parle à cette ouverture : un message peut paraître. */
    val quiet: Boolean get() = tourSeen && !crashPending
}

/** Où l'ouverture lit sa situation. */
fun interface OpeningContext {
    suspend fun current(): OpeningSituation
}

/**
 * La situation, relue des ports qui la tiennent.
 *
 * **Une lecture par ouverture comptée**, jamais à chaque retour sur l'accueil : la plupart
 * des passages ne sont pas des ouvertures, et ceux-là n'ont rien à choisir.
 *
 * Un port qui échoue vaut la réponse prudente — pas de clé, pas de tour vu — et non une
 * exception : un message manqué n'est rien, une ouverture qui plante l'accueil serait tout.
 */
class OpeningSituations(
    private val ai: AiSettings,
    private val tour: TourSettings,
    private val crashes: CrashReports,
) : OpeningContext {
    override suspend fun current(): OpeningSituation = OpeningSituation(
        aiConfigured = runCatching { ai.current() != null }.getOrDefault(false),
        tourSeen = runCatching { tour.observeSeen().first() }.getOrDefault(false),
        crashPending = runCatching { crashes.pending() != null }.getOrDefault(false),
    )
}
