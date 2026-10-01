package app.hexavore

import app.hexavore.domain.goal.Goals
import app.hexavore.domain.identity.IdGenerator
import app.hexavore.domain.profile.Profiles
import app.hexavore.domain.time.Clock
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
}
