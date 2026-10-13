package app.hexavore.domain.ai

import kotlinx.coroutines.flow.Flow

/**
 * La bonne nouvelle d'une clé acceptée, **le temps qu'un écran la dise**.
 *
 * ### Pourquoi un port, et non un résultat de navigation
 *
 * La première version posait la nouvelle sur l'écran précédent, dans l'état de la pile
 * de navigation, et seul le hub des réglages savait l'y lire. Or la plupart des gens
 * n'arrivent pas à l'écran des clés par le hub : ils y arrivent **depuis l'accueil** —
 * le tour guidé, ou le bouton d'analyse grisé qui propose de configurer. Ils revenaient
 * donc sur l'accueil après avoir validé, et rien ne leur disait que c'était fait
 * ([D157][decisions]).
 *
 * Un résultat de navigation suppose de savoir sur quel écran on revient. Ce port ne
 * le suppose pas : la nouvelle attend ici, et **l'écran qui reprend la main la dit**,
 * quel qu'il soit. Celui qui écrit ne connaît pas ceux qui lisent, et l'inverse.
 *
 * ### Ce qu'il ne retient pas
 *
 * Rien n'est écrit sur un disque. Une nouvelle qu'un processus tué emporte avec lui
 * n'est pas perdue : la clé, elle, est enregistrée, et les boutons d'analyse
 * s'allument d'eux-mêmes au lancement suivant. C'est la même raison que le jour
 * regardé, qui vit lui aussi en mémoire.
 *
 * [decisions]: docs/11-decisions.md
 */
interface KeyAcceptance {
    /** La nouvelle en attente, ou `null` quand il n'y a rien à dire. */
    fun observe(): Flow<AcceptedKey?>

    /** Le fournisseur vient d'accepter une clé, **et elle est enregistrée**. */
    fun announce(key: AcceptedKey)

    /** Un écran l'a dite : elle ne doit pas reparaître sur le suivant. */
    fun consume()
}

/**
 * Une clé que le fournisseur vient d'accepter, et ce qu'elle ouvre.
 *
 * @param vision `false` quand le modèle validé ne lit pas les images. La nouvelle
 *   change alors de phrase : promettre la photo sous un modèle qui ne la lit pas serait
 *   mentir au moment précis où l'on vient d'obtenir la confiance de quelqu'un.
 */
data class AcceptedKey(val vision: Boolean)
