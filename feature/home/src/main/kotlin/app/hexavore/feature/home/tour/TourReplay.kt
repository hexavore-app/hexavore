package app.hexavore.feature.home.tour

/**
 * Le tour se rejoue-t-il à chaque lancement, sans tenir compte du souvenir ?
 *
 * ### Pourquoi ce n'est plus une constante
 *
 * C'en était une, avec un commentaire demandant de la remettre à `false` avant
 * publication. Une note qui dépend de quelqu'un pour être lue n'est pas une garantie :
 * le jour où elle serait oubliée, chaque utilisateur reverrait le tour à chaque
 * ouverture, et rien dans la chaîne de livraison ne l'aurait dit.
 *
 * C'est désormais `:app` qui décide, et il le décide sur la variante de build : la
 * version publiée reçoit [NEVER] parce qu'elle ne peut rien recevoir d'autre. Le réglage
 * reste entier pour qui regarde le tour vingt fois de suite, et il ne peut plus sortir
 * de la variante `debug`.
 *
 * ### Et pourquoi un type plutôt qu'un booléen
 *
 * Un `Boolean` injecté se confondrait avec le prochain ; celui-ci dit ce qu'il règle,
 * et l'écriture `TourReplay.NEVER` se lit sans aller chercher ce que `false` voulait
 * dire.
 */
class TourReplay(val always: Boolean) {
    companion object {
        /** Le défaut, et le seul que la variante publiée connaisse. */
        val NEVER = TourReplay(always = false)
    }
}
