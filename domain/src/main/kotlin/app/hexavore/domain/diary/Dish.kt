package app.hexavore.domain.diary

import java.time.Instant
import java.time.LocalDate

/**
 * Identifiant d'un plat.
 *
 * UUIDv4 généré côté application, et non un entier auto-incrémenté : un compteur
 * rendrait toute fusion de sauvegardes impossible et interdirait des identifiants
 * stables entre appareils.
 */
@JvmInline
value class DishId(val value: String)

/**
 * Un plat : plusieurs aliments, entrés en une fois.
 *
 * C'est l'unité de saisie de l'application. Pas de case à choisir avant
 * d'enregistrer : la question qui compte est « qu'est-ce que j'ai mangé aujourd'hui »,
 * pas « à quel repas », et le classement chronologique y répond gratuitement
 * ([D31][decisions]).
 *
 * Un plat porte quand même un **nom** depuis [D118][decisions], et ce n'est pas la même
 * chose : il se déduit de l'heure, ne coûte aucun geste, ne range le plat nulle part,
 * et sert à écrire une liste sans citer tous ses aliments. Voir [title] et [moment].
 *
 * Un plat porte **une** [source], celle par laquelle il est entré. Elle ne change
 * jamais, même après vingt corrections à la main.
 *
 * [decisions]: docs/11-decisions.md
 */
data class Dish(
    val id: DishId,
    val date: LocalDate,
    /** Origine de la saisie. Fixée à la création, jamais réécrite. */
    val source: EntrySource,
    /** Sert au classement : les plats s'affichent dans l'ordre où ils ont été notés. */
    val loggedAt: Instant,
    val entries: List<FoodEntry>,
    /**
     * Le favori dont ce plat a été rejoué, s'il en vient d'un.
     *
     * C'est ce lien qui permet à l'étoile de se rallumer en rouvrant le plat, et il
     * **tombe dès que le contenu est modifié** : un plat corrigé n'est plus celui que
     * le favori décrit ([D62][decisions]).
     *
     * `null` aussi quand le favori a été supprimé depuis — la base le délie plutôt que
     * d'effacer le plat, parce qu'un journal est un registre d'événements et qu'un
     * modèle réutilisable disparu n'a pas à en amputer une journée.
     *
     * [decisions]: docs/11-decisions.md
     */
    val favoriteId: FavoriteDishId? = null,
    /**
     * Le titre écrit à la main, ou `null` — ce qui est le cas courant.
     *
     * `null` ne veut pas dire « sans titre » : le plat s'appelle alors du nom de son
     * [moment], et c'est [titles] qui le compose. Écrire ce nom ici à la place aurait
     * figé des mots français dans la base, là où l'utilisateur n'a rien dit.
     */
    val title: String? = null,
    /**
     * Le moment retenu **à la saisie**.
     *
     * Il vient de l'heure qu'il était, et l'écran de validation permet de le corriger :
     * l'heure d'un plat est celle où on le note, donc un dîner rattrapé le lendemain
     * matin s'appellerait sinon « Petit-déjeuner ».
     *
     * `null` pour les plats écrits avant que les moments existent. Leur heure le dit
     * alors, ce qui est exact dans le cas courant — un repas se note en le mangeant.
     */
    val moment: MealMoment? = null,
)
