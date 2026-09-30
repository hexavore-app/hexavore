package app.hexavore.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Le profil, en **une seule ligne**.
 *
 * `id` vaut toujours `"singleton"` ([docs/07][modele]). Une table à une ligne plutôt
 * qu'un fichier de préférences : ces valeurs entrent dans un calcul, elles doivent
 * partir dans la sauvegarde avec le reste, et elles se migrent comme le reste.
 *
 * **La date de naissance est stockée, jamais l'âge.** Un âge se périme en silence :
 * l'objectif calculé un 3 janvier resterait celui d'une personne d'un an plus jeune,
 * indéfiniment, et rien dans l'interface ne le dirait.
 *
 * Le poids n'est pas ici : il change, on veut sa tendance, et il vit donc dans
 * `weight_entry`.
 *
 * [modele]: docs/07-modele-de-donnees.md
 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = SINGLETON,
    @ColumnInfo(name = "birth_date")
    val birthDate: String,
    @ColumnInfo(name = "sex")
    val sex: String,
    @ColumnInfo(name = "height_cm")
    val heightCm: Double,
    /**
     * Le métier, désormais : `DESK`, `ON_FEET` ou `PHYSICAL`.
     *
     * **La colonne garde son nom** alors que son contenu a changé de sens
     * ([D137][decisions]). La renommer aurait demandé de recréer la table pour un mot,
     * et [docs/07][modele] préfère une colonne nullable à une table nouvelle, et une
     * table nouvelle à un renommage — c'est le renommage qui casse les sauvegardes.
     *
     * [decisions]: docs/11-decisions.md
     * [modele]: docs/07-modele-de-donnees.md
     */
    @ColumnInfo(name = "activity_level")
    val activityLevel: String,
    /** Les séances de sport par semaine, de 0 à 5. */
    @ColumnInfo(name = "leisure_sessions")
    val leisureSessions: Int,
    /** Affichage seulement. Le stockage est **toujours** métrique. */
    @ColumnInfo(name = "unit_system")
    val unitSystem: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    companion object {
        const val SINGLETON = "singleton"
    }
}

/**
 * Une pesée.
 *
 * **`date` est unique** : une pesée par jour, la dernière remplace. Se peser trois
 * fois un matin est courant ; en garder trois donnerait trois fois plus de poids à ce
 * jour-là dans la moyenne mobile, ce qui est précisément le bruit qu'elle sert à
 * retirer.
 *
 * La date est une chaîne `ISO-8601` comme partout ailleurs dans ce schéma : elle se
 * trie lexicographiquement, se lit dans un client SQLite, et ne dépend d'aucun fuseau.
 */
@Entity(tableName = "weight_entry", indices = [Index(value = ["date"], unique = true)])
data class WeightEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "date")
    val date: String,
    @ColumnInfo(name = "weight_kg")
    val weightKg: Double,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

/**
 * Un objectif, **versionné**.
 *
 * Jamais modifié en place : chaque changement crée une ligne, l'ancienne recevant
 * `ended_at` ([D04][decisions]). Une journée est donc toujours comparée à l'objectif
 * qui était le sien.
 *
 * **Invariant : au plus une ligne avec `ended_at IS NULL`.** Il est tenu par l'index
 * unique ci-dessous, et non par une convention d'écriture. SQLite ne compare jamais
 * deux `NULL` comme égaux, ce qui rendrait un index sur `ended_at` seul inopérant :
 * l'index porte donc sur une **colonne calculée** qui vaut `1` pour l'objectif actif
 * et l'identifiant sinon — deux lignes actives entreraient alors en collision.
 *
 * `origin` porte à lui seul la provenance des six chiffres. La colonne `manual_fields`,
 * qui listait les compteurs figés à l'intérieur d'un objectif calculé, est partie en
 * migration 3 → 4 : le verrou par compteur est devenu un mode ([D60][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Entity(
    tableName = "goal",
    indices = [
        Index(value = ["active_key"], unique = true),
        Index(value = ["started_at"]),
    ],
)
data class GoalEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "started_at")
    val startedAt: String,
    @ColumnInfo(name = "ended_at")
    val endedAt: String?,
    /**
     * `1` tant que l'objectif court, l'identifiant une fois clos.
     *
     * Écrite plutôt que déduite : Room ne sait pas exprimer d'index partiel, et un
     * index unique sur `ended_at` ne contraindrait rien puisque deux `NULL` ne se
     * heurtent pas. C'est de la redondance, et elle est là pour que la base **refuse**
     * deux objectifs actifs plutôt que pour qu'on se souvienne de ne pas les écrire.
     */
    @ColumnInfo(name = "active_key")
    val activeKey: String,
    @ColumnInfo(name = "origin")
    val origin: String,
    @ColumnInfo(name = "strategy")
    val strategy: String,
    @ColumnInfo(name = "target_weight_kg")
    val targetWeightKg: Double?,
    @ColumnInfo(name = "target_date")
    val targetDate: String?,
    @ColumnInfo(name = "kcal")
    val kcal: Double,
    @ColumnInfo(name = "protein_g")
    val proteinG: Double,
    @ColumnInfo(name = "carb_g")
    val carbG: Double,
    @ColumnInfo(name = "sugar_g")
    val sugarG: Double,
    @ColumnInfo(name = "fat_g")
    val fatG: Double,
    @ColumnInfo(name = "fiber_g")
    val fiberG: Double,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
) {
    companion object {
        /** La valeur d'`active_key` d'un objectif qui court. Il ne peut y en avoir qu'un. */
        const val ACTIVE = "1"
    }
}
