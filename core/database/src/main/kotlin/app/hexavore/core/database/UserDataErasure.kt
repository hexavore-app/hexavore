package app.hexavore.core.database

/**
 * Vide tout ce que l'utilisateur a écrit.
 *
 * **N'ouvre pas de transaction** : l'appelant décide. Une restauration vide puis
 * réécrit sous une seule transaction ; un effacement se contente de la sienne.
 *
 * **Un balayage plutôt que huit méthodes de DAO.** Room aurait demandé un
 * `@Query("DELETE FROM …")` par table, soit huit déclarations qui disent la même chose
 * — et un DAO de plus sur une base qui en compte déjà dix. Ici la règle tient en une
 * liste, et cette liste **est** la question qu'on se pose en la relisant : qu'est-ce
 * qui appartient à l'utilisateur ?
 *
 * La base de référence de l'ANSES n'y figure pas : elle vit dans un autre fichier,
 * en lecture seule, et se réinstalle avec l'APK.
 *
 * @see docs/09-donnees-et-sauvegarde.md
 */
fun HexavoreDatabase.eraseUserData() {
    USER_TABLES.forEach { openHelper.writableDatabase.execSQL("DELETE FROM $it") }
}

/**
 * Les tables du contenu utilisateur, **parents d'abord**.
 *
 * `food_entry` et `favorite_component` n'y sont pas : leurs clés étrangères sont en
 * `CASCADE`, et vider `dish` et `favorite_dish` les emporte. Les nommer quand même
 * marcherait, mais laisserait croire que la cascade n'existe pas — et le jour où
 * quelqu'un la retirerait, rien ne le dirait.
 */
private val USER_TABLES = listOf(
    "favorite_dish",
    "dish",
    "food",
    "weight_entry",
    "goal",
    "profile",
    // La progression est du contenu utilisateur comme le reste : « effacer mes
    // donnees » qui laisserait les paliers en place rendrait une application a moitie
    // neuve, ou l'on repart de zero avec le niveau de quelqu'un d'autre.
    "progress",
    "unlocked_badge",
)
