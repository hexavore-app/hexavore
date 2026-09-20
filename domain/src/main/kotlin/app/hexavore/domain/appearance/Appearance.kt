package app.hexavore.domain.appearance

import kotlinx.coroutines.flow.Flow

/**
 * Le thème que l'utilisateur a choisi.
 *
 * **[SYSTEM] est un choix, pas une absence de choix.** C'est l'écart avec le jour
 * regardé, où `null` veut dire aujourd'hui : là, la valeur nulle protégeait d'un écran
 * qui resterait sur la veille après minuit. Ici, « suivre le système » est une intention
 * durable, et la ranger comme un vide obligerait chaque lecteur à savoir ce que le vide
 * signifie.
 */
enum class ThemeMode {
    LIGHT,
    DARK,

    /** Ce que l'application faisait avant qu'on puisse choisir, et le défaut. */
    SYSTEM,
    ;

    /**
     * Faut-il peindre sombre ?
     *
     * **Une fonction pure plutôt qu'un `when` dans la composition.** Le réglage
     * d'Android arrive en paramètre : la règle s'éprouve alors sans écran, et il n'y a
     * qu'un endroit qui sache ce que « suivre le système » veut dire.
     */
    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemIsDark
    }
}

/**
 * Ce que l'utilisateur a réglé sur l'apparence de l'application.
 *
 * **Une préférence d'appareil, et elle ne voyage pas.** [D96][decisions] a fixé que la
 * sauvegarde ne porte que ce que la base tient ; un thème vit dans les préférences, et
 * restaurer un export sur un autre téléphone n'a aucune raison d'y imposer le thème du
 * premier. Le système d'unités, lui, est une propriété du profil et voyage avec lui.
 *
 * **Son propre fichier de préférences.** Effacer ses clés d'IA remet à zéro les réglages
 * d'IA ; l'apparence n'a rien à voir avec elles et n'a pas à disparaître avec.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/02-parcours-et-ecrans.md
 */
interface AppearanceSettings {
    fun observeTheme(): Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)

    fun observeDishStyle(): Flow<DishDisplayStyle>

    suspend fun setDishStyle(style: DishDisplayStyle)
}

/**
 * La façon dont l'accueil montre un plat.
 *
 * **Deux styles et non un réglage de densité.** Ce qui distingue les deux n'est pas une
 * hauteur de ligne : c'est ce qu'on lit. Le simplifié répond à « qu'est-ce que j'ai
 * mangé et combien ça pèse » ; le détaillé répond à « de quoi était-ce fait ». Une
 * échelle de densité aurait fait croire qu'il existe un entre-deux.
 *
 * **Une préférence d'appareil, comme le thème** ([D113][decisions]) : elle ne voyage pas
 * dans la sauvegarde, parce qu'elle ne dit rien de ce qui a été mangé.
 *
 * [decisions]: docs/11-decisions.md
 */
enum class DishDisplayStyle {
    /**
     * Le titre, l'heure, les calories, les cinq apports. Pas la liste des aliments.
     *
     * **Le défaut** : l'affichage détaillé cite chaque aliment de chaque plat, ce qui
     * fait beaucoup de texte dès qu'une journée est chargée — et la question qu'on se
     * pose dix fois par jour n'est pas « quels aliments », c'est « où j'en suis ».
     */
    SIMPLE,

    /** Le simplifié, plus chaque ligne d'aliment : son nom, sa quantité, ses calories. */
    DETAILED,
}
