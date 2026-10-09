package app.hexavore.domain.tour

import kotlinx.coroutines.flow.Flow

/**
 * Le tour guidé a-t-il déjà eu lieu ?
 *
 * ### Un réglage, et non une étape de l'onboarding
 *
 * L'onboarding pose cinq questions pour **calculer un objectif** : il ne peut pas être
 * sauté, et sans lui l'application n'a rien à afficher. Le tour, lui, montre des gestes
 * sur un écran qui existe déjà. Le fondre dans l'onboarding aurait fait d'un
 * commentaire une sixième question obligatoire.
 *
 * C'est aussi pourquoi il porte son propre souvenir : quelqu'un qui installait
 * l'application avant que le tour existe a un objectif mais n'a rien vu, et c'est à lui
 * qu'il faut le montrer ([D141][decisions]).
 *
 * ### Il ne se rejoue pas tout seul
 *
 * Vu une fois, oublié jamais — la seule chose qui le ramène est un effacement complet,
 * qui ramène aussi tout le reste. Un tour qui reviendrait « au cas où » serait une
 * punition pour qui ouvre son application vingt fois par jour.
 *
 * [decisions]: docs/11-decisions.md
 */
interface TourSettings {
    fun observeSeen(): Flow<Boolean>

    suspend fun markSeen()
}
