package app.hexavore.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Ecrites plutot que posees dans l'appel : les bornes d'une migration sont ce qu'on
// verifie en premier quand une chaine ne s'applique pas, et un litteral au milieu
// d'une liste de supertypes ne se voit pas.
private const val FROM_VERSION = 8
private const val TO_VERSION = 9

/**
 * Version 8 → 9 : l'activité se dit en deux réponses.
 *
 * Une colonne — `leisure_sessions` — et une **traduction des lignes existantes**
 * ([D137][decisions]). `activity_level` garde son nom et change de sens : il portait un
 * niveau unique, il porte désormais un métier.
 *
 * ### Pourquoi la traduction se fait ici, et pas à la lecture
 *
 * Un mappeur qui traduirait à chaque lecture porterait l'ancienne échelle pour
 * toujours, et le jour où quelqu'un ouvrirait `profile` dans un client SQLite, il y
 * lirait `MODERATE` dans une colonne qui prétend nommer un métier. La migration est
 * l'endroit où une base change de sens — c'est ce pour quoi elle existe.
 *
 * ### L'ordre des deux mises à jour compte
 *
 * Les séances se calculent **depuis l'ancien niveau**, donc avant que celui-ci soit
 * réécrit. Inversées, les deux instructions liraient un métier là où elles attendent un
 * niveau, et tout le monde se retrouverait à zéro séance.
 *
 * ### Ce que la traduction conserve
 *
 * Le **facteur**, à moins de deux centièmes près : l'objectif calculé ne bouge donc pas
 * de façon perceptible. Les objectifs déjà écrits, eux, ne bougent pas du tout — ils
 * sont versionnés et personne ne les recalcule ([D04][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
internal object Migration8To9 : Migration(FROM_VERSION, TO_VERSION) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `leisure_sessions` INTEGER NOT NULL DEFAULT 0")

        // Les seances d'abord : elles lisent l'ancien niveau, que l'instruction
        // suivante remplace.
        db.execSQL(
            """
            UPDATE `profile` SET `leisure_sessions` = CASE `activity_level`
                WHEN 'SEDENTARY' THEN 0
                WHEN 'LIGHT' THEN 3
                WHEN 'MODERATE' THEN 3
                WHEN 'ACTIVE' THEN 2
                WHEN 'VERY_ACTIVE' THEN 5
                ELSE 0
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            UPDATE `profile` SET `activity_level` = CASE `activity_level`
                WHEN 'SEDENTARY' THEN 'DESK'
                WHEN 'LIGHT' THEN 'DESK'
                WHEN 'MODERATE' THEN 'ON_FEET'
                WHEN 'ACTIVE' THEN 'PHYSICAL'
                WHEN 'VERY_ACTIVE' THEN 'PHYSICAL'
                ELSE 'DESK'
            END
            """.trimIndent(),
        )
    }
}
