package app.hexavore.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * La progression, en **une seule ligne**.
 *
 * `id` vaut toujours `"singleton"`, comme `profile` : une table à une ligne plutôt
 * qu'un fichier de préférences, parce que ces valeurs partent dans la sauvegarde avec
 * le reste et se migrent comme le reste.
 *
 * **Trois planchers, et rien d'autre.** Ce que le journal peut redire n'est pas ici :
 * la série en cours, le nombre de plats et les points du jour se recalculent depuis
 * les plats eux-mêmes, qui sont la seule vérité de ce qui a été mangé. Ce qui est rangé
 * ici est ce qu'aucun recalcul ne retrouverait — un record atteint il y a deux ans, un
 * total de points qu'une correction ferait baisser ([D132][decisions]).
 *
 * **Ils ne descendent jamais.** Un plat supprimé ne doit pas coûter un niveau, sans
 * quoi corriger une erreur serait puni et personne ne corrigerait plus rien.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/07-modele-de-donnees.md
 */
@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = SINGLETON,
    @ColumnInfo(name = "points")
    val points: Long,
    @ColumnInfo(name = "best_streak")
    val bestStreak: Int,
    @ColumnInfo(name = "best_perfect_streak")
    val bestPerfectStreak: Int,
) {
    companion object {
        const val SINGLETON = "singleton"
    }
}

/**
 * Un palier franchi, et le jour où il l'a été.
 *
 * **Le nom du palier est la clé primaire** : il ne se franchit qu'une fois, et une
 * table qui pourrait en porter deux exemplaires obligerait chaque lecture à choisir
 * lequel croire.
 *
 * `badge` est le nom de l'énumération, écrit tel quel. Un palier retiré du code laisse
 * donc une ligne que plus rien ne lit — c'est voulu : la supprimer effacerait la date à
 * laquelle quelqu'un l'a obtenu, et une restauration sur une version plus ancienne la
 * retrouverait.
 *
 * `unlocked_on` est une date `ISO-8601`, comme partout ailleurs dans ce schéma.
 */
@Entity(tableName = "unlocked_badge")
data class UnlockedBadgeEntity(
    @PrimaryKey
    @ColumnInfo(name = "badge")
    val badge: String,
    @ColumnInfo(name = "unlocked_on")
    val unlockedOn: String,
)
