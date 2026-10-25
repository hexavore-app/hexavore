package app.hexavore.domain.ai

import app.hexavore.domain.diary.EntryDraft
import java.util.concurrent.atomic.AtomicReference

/**
 * Ce qu'un modèle vient de proposer, **déjà résolu**, en attendant l'écran qui le validera.
 *
 * **Une route ne peut pas porter cinq lignes**, et `EntryDestination` l'avait écrit
 * avant que le cas existe : les arguments de navigation sont sérialisés dans l'état du
 * système, et y faire transiter un plat entier reviendrait à en tenir une seconde
 * copie que rien ne tiendrait à jour. Le canal de résultat qui sert à « Ajouter un
 * aliment » ne convient pas davantage — il rend une valeur à l'écran **précédent**,
 * alors qu'ici l'écran destinataire n'existe pas encore.
 *
 * D'où ce dépôt, et **il ne contient jamais qu'une proposition** : celle qui vient
 * d'être faite. Il n'y a pas de file d'attente parce qu'il n'y a pas deux analyses en
 * vol — l'écran de capture attend la sienne avant de laisser en lancer une autre.
 *
 * **Ce qui est déposé est un brouillon, et non plus la réponse du modèle**
 * ([D176][decisions]). La résolution demande le catalogue, et aussi, pour ce qu'il ne
 * connaît pas, un second appel au fournisseur. Faite à l'ouverture de l'écran de
 * validation, elle le laissait noir le temps de cet appel — jusqu'à une minute, sans
 * rien pour le dire ni pour l'annuler. Elle se fait donc pendant l'analyse, sous
 * l'indicateur qui attend déjà, et l'écran de validation ouvre ce qu'on lui remet. Elle
 * reste en un seul endroit, `ResolveRecognition`, que l'analyse appelle.
 *
 * **Volatil, et c'est voulu.** Une proposition ne survit pas à la mort du processus :
 * ce qui a été payé au fournisseur est perdu, et l'écran de validation dira qu'il n'y
 * a rien à valider. La persister demanderait de décider où, combien de temps, et sous
 * quelle forme une proposition périmée se relit — trois questions qu'aucun usage ne
 * pose aujourd'hui.
 *
 * [decisions]: docs/11-decisions.md
 */
interface PendingProposal {
    /** Dépose le brouillon proposé. Un second remplace le premier. */
    fun offer(draft: EntryDraft)

    /**
     * Reprend le brouillon et **vide le dépôt**.
     *
     * Consommé plutôt que lu : revenir sur l'écran de validation par le bouton
     * « retour » du système ne doit pas ressusciter un plat qu'on vient d'enregistrer,
     * ni le dédoubler. `null` quand il n'y a rien — la deuxième ouverture, ou un
     * processus qui a redémarré entre les deux écrans.
     */
    fun take(): EntryDraft?
}

/**
 * Le dépôt, en mémoire.
 *
 * Dans le domaine et non dans un module de données : il ne range rien, il fait passer.
 * Le rendre persistant serait un changement de nature, pas d'implémentation.
 *
 * `AtomicReference` parce que l'analyse finit sur un dispatcher d'entrées-sorties et
 * que la reprise a lieu sur le thread principal.
 */
class InMemoryPendingProposal : PendingProposal {
    private val pending = AtomicReference<EntryDraft?>(null)

    override fun offer(draft: EntryDraft) {
        pending.set(draft)
    }

    override fun take(): EntryDraft? = pending.getAndSet(null)
}
