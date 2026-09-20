package app.hexavore.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Ecrites plutot que posees dans l'appel : les bornes d'une migration sont ce qu'on
// verifie en premier quand une chaine ne s'applique pas, et un litteral au milieu
// d'une liste de supertypes ne se voit pas.
private const val FROM_VERSION = 6
private const val TO_VERSION = 7

/**
 * Version 6 → 7 : un plat porte un nom.
 *
 * Deux colonnes sur `dish` — `title`, le nom écrit à la main, et `moment`, celui que
 * la saisie a retenu ([D118][decisions]).
 *
 * **Les deux restent à `NULL` sur les plats existants, et c'est exact.** Personne n'a
 * nommé ces plats ni choisi leur moment : l'application ne le demandait pas. Leur
 * moment se déduit de leur heure à l'affichage, ce qui est la bonne lecture dans le
 * cas courant — un repas se note en le mangeant. Écrire un moment ici affirmerait un
 * choix que personne n'a fait, et le ferait pour des mois de journal d'un coup.
 *
 * **La table n'est pas recréée**, comme en 5 → 6 : `ALTER TABLE … ADD COLUMN` existe
 * dans toutes les versions de SQLite, et rien ne touche aux deux index de `dish` ni à
 * sa clé étrangère vers `favorite_dish`. Un test de comportement l'affirme quand même
 * — c'est la propriété qui compte, pas le moyen.
 *
 * [decisions]: docs/11-decisions.md
 */
internal object Migration6To7 : Migration(FROM_VERSION, TO_VERSION) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `dish` ADD COLUMN `title` TEXT")
        db.execSQL("ALTER TABLE `dish` ADD COLUMN `moment` TEXT")
    }
}
