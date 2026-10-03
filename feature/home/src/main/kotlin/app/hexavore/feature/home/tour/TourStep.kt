package app.hexavore.feature.home.tour

import androidx.annotation.StringRes
import app.hexavore.feature.home.R

/**
 * Les étapes du tour, dans l'ordre où elles se racontent.
 *
 * ### L'ordre dit quelque chose
 *
 * D'abord **ce qu'on obtient** — une journée remplie, l'hexagone qui se colore — parce
 * qu'un outil se présente par ce qu'il rend, pas par ses boutons. Puis les six
 * compteurs un par un : ce sont eux que l'application passe sa vie à montrer, et aucun
 * ne se devine depuis une lettre à la pointe d'un hexagone ([D143][decisions]).
 *
 * Viennent ensuite les gestes, du plus rapide au plus sûr : décrire, photographier, puis
 * tout le reste. L'IA arrive en avant-dernier et non en premier : proposée avant qu'on
 * ait vu à quoi elle sert, elle n'est qu'une demande de clé.
 *
 * ### La dernière n'arrive pas toujours
 *
 * [DEGRADED] ne se montre qu'à qui a refusé la clé. C'est la contrepartie du refus :
 * l'application continue de marcher, et elle doit dire exactement ce qu'elle a perdu
 * plutôt que de le laisser découvrir devant une barre rouge sans explication.
 *
 * [decisions]: docs/11-decisions.md
 */
enum class TourStep(@StringRes val title: Int, @StringRes val body: Int, val target: TourTarget?) {
    DAY(R.string.tour_day_title, R.string.tour_day_body, TourTarget.DAY),

    CALORIES(R.string.tour_calories_title, R.string.tour_calories_body, TourTarget.CALORIES),
    PROTEIN(R.string.tour_protein_title, R.string.tour_protein_body, TourTarget.PROTEIN),
    CARBS(R.string.tour_carbs_title, R.string.tour_carbs_body, TourTarget.CARBS),
    SUGARS(R.string.tour_sugars_title, R.string.tour_sugars_body, TourTarget.SUGARS),
    FAT(R.string.tour_fat_title, R.string.tour_fat_body, TourTarget.FAT),
    FIBER(R.string.tour_fiber_title, R.string.tour_fiber_body, TourTarget.FIBER),

    CALENDAR(R.string.tour_calendar_title, R.string.tour_calendar_body, TourTarget.CALENDAR),

    DESCRIBE(R.string.tour_describe_title, R.string.tour_describe_body, TourTarget.FIELD),
    PHOTO(R.string.tour_photo_title, R.string.tour_photo_body, TourTarget.CAMERA),
    MORE(R.string.tour_more_title, R.string.tour_more_body, TourTarget.MORE),

    SETTINGS(R.string.tour_settings_title, R.string.tour_settings_body, TourTarget.SETTINGS),

    /** Sans ancre : c'est une proposition, pas une désignation. */
    AI(R.string.tour_ai_title, R.string.tour_ai_body, null),

    DEGRADED(R.string.tour_degraded_title, R.string.tour_degraded_body, TourTarget.FIELD),
    ;

    /**
     * L'étape d'après, ou `null` quand il n'y en a plus.
     *
     * L'ordre de l'énumération fait l'enchaînement : une étape ajoutée au bon endroit
     * s'insère sans qu'on ait à récrire une table de correspondances. Les deux seuls
     * cas particuliers sont la fin.
     */
    fun next(keyless: Boolean): TourStep? = when (this) {
        // Sans cle, le tour finit par ce qu'on vient de refuser ; avec, il n'y a plus
        // rien a dire.
        AI -> DEGRADED.takeIf { keyless }
        DEGRADED -> null
        else -> entries[ordinal + 1]
    }
}
