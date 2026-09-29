package app.hexavore.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Ecrites plutot que posees dans l'appel : les bornes d'une migration sont ce qu'on
// verifie en premier quand une chaine ne s'applique pas, et un litteral au milieu
// d'une liste de supertypes ne se voit pas.
private const val FROM_VERSION = 7
private const val TO_VERSION = 8

/**
 * Version 7 → 8 : la progression a une mémoire.
 *
 * Deux tables neuves — `progress`, qui tient trois planchers en une ligne, et
 * `unlocked_badge`, un palier par ligne avec le jour où il est tombé ([D132][decisions]).
 *
 * **Rien n'est rempli, et c'est exact.** Une base existante porte peut-être des mois de
 * journal ; la progression qu'elle vaut se recalcule au premier affichage, depuis les
 * plats eux-mêmes, et les planchers se poseront tout seuls. Écrire ici des valeurs
 * déduites du journal referait ce calcul une seconde fois, dans un endroit qui n'a ni
 * les objectifs versionnés ni la règle de la journée parfaite — donc les referait faux.
 *
 * L'absence de ligne dans `progress` **est** la valeur de départ : le dépôt la lit
 * comme trois zéros, ce qui est la vérité d'une progression qui n'a encore rien figé.
 * Une ligne écrite ici à zéro dirait la même chose, moins clairement, et un seul chemin
 * vaut mieux que deux ([D92][decisions] pour les journées sans saisie, même
 * raisonnement).
 *
 * **Aucune table n'est recréée**, et rien n'est touché ailleurs : deux `CREATE TABLE`
 * sur une base qui ne les connaît pas ne peuvent rien casser de ce qui existe.
 *
 * [decisions]: docs/11-decisions.md
 */
internal object Migration7To8 : Migration(FROM_VERSION, TO_VERSION) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `progress` (
                `id` TEXT NOT NULL,
                `points` INTEGER NOT NULL,
                `best_streak` INTEGER NOT NULL,
                `best_perfect_streak` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `unlocked_badge` (
                `badge` TEXT NOT NULL,
                `unlocked_on` TEXT NOT NULL,
                PRIMARY KEY(`badge`)
            )
            """.trimIndent(),
        )
    }
}
