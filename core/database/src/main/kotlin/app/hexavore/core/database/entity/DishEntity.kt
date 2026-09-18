package app.hexavore.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un plat : plusieurs aliments, entrés en une fois.
 *
 * `source` n'est **jamais réécrite**. Un plat reste éditable à la main
 * indéfiniment ; son origine est un fait historique, pas un état. Corriger une
 * quantité sur une proposition de l'IA ne doit pas la faire passer pour une saisie
 * manuelle — ce serait perdre la seule trace de ce qui a été deviné.
 *
 * `date` est la journée **locale** à laquelle le plat est rattaché, en ISO-8601 :
 * triable en SQL, lisible à l'œil dans un export. `loggedAt` est l'instant en
 * millisecondes UTC, et sert au classement à l'intérieur d'une journée.
 *
 * @see docs/07-modele-de-donnees.md
 * @see docs/11-decisions.md — D31, D32
 */
@Entity(
    tableName = "dish",
    // L'accueil lit une journée et l'affiche dans l'ordre : c'est l'index qui rend
    // cette lecture immédiate, et c'est la requête la plus fréquente de l'application.
    indices = [Index(value = ["date", "logged_at"]), Index(value = ["favorite_id"])],
    foreignKeys = [
        ForeignKey(
            entity = FavoriteDishEntity::class,
            parentColumns = ["id"],
            childColumns = ["favorite_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class DishEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "date")
    val date: String,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "logged_at")
    val loggedAt: Long,
    /**
     * Le favori dont ce plat a été rejoué, s'il en vient d'un.
     *
     * En `SET NULL` : supprimer un favori **délie** les plats qui en venaient au lieu
     * de les effacer. Un journal est un registre d'événements, et le modèle qui a servi
     * à composer un repas n'a pas à emporter le repas en disparaissant — c'est la même
     * règle que pour un aliment personnel supprimé ([D62][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    @ColumnInfo(name = "favorite_id")
    val favoriteId: String? = null,
    /**
     * Le titre écrit à la main, ou `NULL` — ce qui est le cas courant.
     *
     * `NULL` ne veut pas dire « sans titre » : le plat s'appelle alors du nom de son
     * [moment]. Y écrire ce nom aurait figé des mots français dans la base, là où
     * l'utilisateur n'a rien dit.
     */
    @ColumnInfo(name = "title")
    val title: String? = null,
    /**
     * Le moment retenu à la saisie, ou `NULL` pour les plats d'avant les moments.
     *
     * Leur heure le dit alors, ce qui est exact dans le cas courant — un repas se note
     * en le mangeant. Le remplir à la migration aurait affirmé de trois mois de
     * journal un moment que personne n'a choisi.
     */
    @ColumnInfo(name = "moment")
    val moment: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
