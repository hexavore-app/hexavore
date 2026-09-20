package app.hexavore.domain.diary

import java.time.ZoneId

/**
 * Le titre d'un plat, tel qu'un écran l'écrira.
 *
 * **Deux formes et non une chaîne**, parce que ce sont deux choses différentes : ce
 * que l'utilisateur a écrit se rend tel quel, ce qui est déduit doit encore être
 * traduit en mots — et le domaine n'en écrit pas ([NextFavoriteNumber][numbering] avait
 * déjà tranché la question pour « Plat 3 »).
 *
 * [numbering]: app.hexavore.domain.usecase.ProposeFavoriteName
 */
sealed interface DishTitle {
    /** Ce que l'utilisateur a écrit lui-même. Jamais vide. */
    data class Named(val text: String) : DishTitle

    /**
     * Le moment du plat, et son **rang** parmi ceux du même moment ce jour-là.
     *
     * [rank] vaut 1 pour le premier, 2 pour le suivant : « Déjeuner », puis
     * « Déjeuner 2 » quand le dessert est noté à part. Sans ce rang, deux plats
     * d'affilée porteraient exactement le même nom dans une liste dont c'est
     * justement le seul repère en affichage simplifié.
     *
     * **Les plats nommés à la main ne comptent pas dans le rang.** Ce que
     * l'utilisateur a écrit lui appartient : si deux plats s'appellent tous deux
     * « Poke bowl », c'est qu'il l'a voulu, et rien n'ira leur coller un numéro.
     */
    data class Moment(val moment: MealMoment, val rank: Int) : DishTitle
}

/**
 * Les titres des plats d'une journée, dans leur ordre.
 *
 * Calculé pour la journée entière et non plat par plat, parce que le rang est une
 * propriété du **voisinage** : un plat ne sait pas seul s'il est le deuxième déjeuner.
 *
 * @param zone le fuseau dans lequel lire l'heure d'un plat — celui du résumé qui
 *   l'affiche, jamais celui de la machine.
 */
fun List<Dish>.titles(zone: ZoneId): List<DishTitle> {
    val seen = mutableMapOf<MealMoment, Int>()

    return map { dish ->
        val named = dish.title?.takeIf { it.isNotBlank() }
        if (named != null) {
            DishTitle.Named(named)
        } else {
            val moment = dish.momentIn(zone)
            val rank = seen.getOrDefault(moment, 0) + 1
            seen[moment] = rank
            DishTitle.Moment(moment, rank)
        }
    }
}

/**
 * Le moment auquel ce plat se rattache.
 *
 * Celui qui a été **retenu à la saisie** quand il y en a un, et sinon celui que dit
 * l'heure du plat. Les deux ne se valent pas : un dîner noté le lendemain matin porte
 * `DINNER` parce que quelqu'un l'a corrigé, là où son heure dirait le contraire.
 *
 * `null` n'arrive que pour les plats écrits avant que les moments existent. Les
 * déduire de leur heure est la seule lecture possible, et c'est la bonne dans le cas
 * courant — un repas se note en le mangeant.
 */
fun Dish.momentIn(zone: ZoneId): MealMoment = moment ?: MealMoment.at(loggedAt.atZone(zone).toLocalTime())
