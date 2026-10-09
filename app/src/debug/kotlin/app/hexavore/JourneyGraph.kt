package app.hexavore

import app.hexavore.domain.goal.Goals
import app.hexavore.domain.identity.IdGenerator
import app.hexavore.domain.profile.Profiles
import app.hexavore.domain.time.Clock
import app.hexavore.domain.tour.TourSettings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * De quoi écrire un profil sans passer par cinq écrans.
 *
 * Un point d'entrée Hilt **sur le graphe réel** : il ne remplace rien, il emprunte ce
 * que l'application a déjà construit.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface JourneyGraph {
    fun profiles(): Profiles

    fun goals(): Goals

    fun ids(): IdGenerator

    fun clock(): Clock

    /**
     * Le souvenir du tour guidé, **en lecture seule de fait**.
     *
     * Il est emprunté pour une raison précise : prouver qu'un « Passer » a bien **écrit**
     * ce souvenir, et non seulement fait disparaître une bulle. Les deux se ressemblent à
     * l'écran et ne se ressemblent pas du tout au lancement suivant — c'est exactement
     * l'écart qui a laissé un utilisateur revoir le tour à chaque ouverture.
     *
     * Le vérifier par l'écran demanderait un second processus, puisque [TourViewModel]
     * retient dans le processus qu'on lui a déjà répondu : un deuxième lancement ne
     * montrerait pas le tour même si rien n'avait été écrit, et le test passerait au vert
     * en prouvant le contraire de ce qu'il cherche.
     */
    fun tour(): TourSettings
}
