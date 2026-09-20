package app.hexavore.feature.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ce qu'un défilement doit céder au repli du calendrier.
 *
 * **Un doigt qui part vers le haut replie d'abord, puis la page suit.** Le delta qui
 * déclenche le repli est consommé : sans cela la page se déplacerait pendant que la
 * hauteur du calendrier s'anime, et le contenu ferait un bond.
 *
 * Trois cas ne replient rien, et chacun pour sa raison :
 *
 * - **le calendrier est déjà replié** — il n'y a rien à fermer ;
 * - **le doigt descend** — on remonte dans la page, et refermer ce qu'on vient
 *   d'ouvrir à ce moment-là serait tout le contraire du geste ;
 * - **le geste est horizontal** — c'est le bandeau qui change de semaine.
 *
 * **Cette fonction ne dit pas *où* le geste a eu lieu**, et c'est le second défaut
 * rapporté à l'usage : la connexion était posée sur toute la page, calendrier compris,
 * et `onPreScroll` va du parent vers l'enfant. Défiler *dans* le mois déplié le
 * refermait donc au lieu de le faire défiler. La réponse n'est pas ici mais dans la
 * disposition — la connexion n'est plus un ancêtre du calendrier, seulement du contenu
 * qui le suit ([D103][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
internal fun collapsingDelta(expanded: Boolean, available: Offset): Offset =
    if (expanded && available.y < 0f) available else Offset.Zero

/**
 * Ce qu'une traction accumule quand la page est déjà en haut.
 *
 * **Le geste d'ouverture manquait.** La poignée seule ne se trouvait pas — « peu de
 * personnes comprennent que le calendrier est développable » —, alors que le geste
 * naturel est déjà connu de tous : tirer vers le bas quand on est en haut.
 *
 * **[atTop] est demandé, et non déduit.** La première version lisait ce que le
 * défilement n'avait pas pu consommer, ce qui disait « on est en haut » sans avoir à le
 * demander. C'était plus joli, et ça ne marchait pas : l'effet d'étirement d'Android
 * consomme précisément ce reste pour dessiner son rebond, et la traction n'accumulait
 * jamais rien. Constaté sur appareil, pas déduit.
 *
 * Quatre cas remettent le compteur à zéro, et chacun pour sa raison :
 *
 * - **le calendrier est déjà déplié** — il n'y a plus rien à ouvrir ;
 * - **la page n'est pas en haut** — on remonte dans la journée, on ne demande rien ;
 * - **le geste n'est pas un doigt** — un défilement lancé qui bute en haut est un
 *   arrêt, pas une intention ; sans cela, toute lecture rapide finirait par déplier
 *   le mois ;
 * - **le doigt ne descend pas** — on défile la page, et l'accumulation d'un geste
 *   précédent n'a plus à traîner.
 */
internal fun pulledBy(previous: Float, expanded: Boolean, atTop: Boolean, available: Offset, byUser: Boolean): Float {
    // Deux moitiés nommées plutôt qu'une condition de quatre termes : l'une dit ce que
    // **le geste** est, l'autre ce que **l'écran** permet. C'est aussi le seuil de
    // complexité de detekt, et la réponse du projet est de séparer selon ce que les
    // choses sont.
    val tire = byUser && available.y > 0f
    val ouvrable = !expanded && atTop

    return if (tire && ouvrable) previous + available.y else 0f
}

/**
 * La distance au-delà de laquelle une traction est une intention.
 *
 * Un centimètre de doigt, environ. En deçà, c'est le rebond de fin de course qu'on
 * obtient en arrivant en haut d'une liste, et il ne demande rien.
 */
internal val ExpandPull: Dp = 48.dp
