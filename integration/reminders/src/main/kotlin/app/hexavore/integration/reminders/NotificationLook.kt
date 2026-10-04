package app.hexavore.integration.reminders

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes

/**
 * À quoi ressemble une notification de l'application : son signe, et sa teinte.
 *
 * ### Pourquoi ça vient de `:app`
 *
 * Ce module sait **quand** rappeler ; il ne sait pas à quoi l'application ressemble.
 * L'icône et la couleur vivent dans le design system, dont une intégration ne dépend
 * pas — c'est cette flèche-là qui tient l'architecture debout. `:app` assemble les deux,
 * comme il le fait déjà pour le rejeu du tour ([D152][decisions]).
 *
 * ### Et pourquoi un type plutôt que deux entiers
 *
 * Deux `Int` injectés se confondraient à la première inversion d'arguments, et rien ne
 * l'aurait dit : une icône et une couleur sont toutes deux des identifiants de
 * ressource. Nommés, ils ne se mélangent plus.
 *
 * [decisions]: docs/11-decisions.md
 */
class NotificationLook(
    /** Une silhouette blanche sur fond transparent : le système n'en garde que l'alpha. */
    @DrawableRes val icon: Int,
    /** La teinte posée sur l'icône et sur le nom de l'application. */
    @ColorRes val color: Int,
)
