package app.hexavore.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.hexavore.domain.progress.Badge

/**
 * Le nom d'un palier, pour la ligne que la célébration affiche.
 *
 * **Une phrase par famille et non par palier.** Dix-huit libellés diraient dix-huit
 * fois la même chose avec un nombre différent, et la traduction les porterait tous ;
 * seules les quatre découvertes ont leur propre phrase, parce que ce sont quatre gestes
 * différents et non quatre niveaux d'un même geste.
 *
 * Il est recopié de l'écran de progression, qui a le sien — et c'est délibéré : un
 * `:feature` ne dépend jamais d'un autre ([docs/06][archi]). Le faire descendre dans le
 * design system l'y ferait vivre à côté de composants qui, eux, ne connaissent aucun
 * palier ; deux courtes traductions valent mieux qu'une dépendance qui n'a pas lieu
 * d'être.
 *
 * [archi]: docs/06-architecture.md
 */
@Composable
internal fun badgeLabel(badge: Badge): String = when (badge) {
    Badge.FIRST_SCAN -> stringResource(R.string.home_badge_first_scan)
    Badge.FIRST_PHOTO -> stringResource(R.string.home_badge_first_photo)
    Badge.FIRST_TEXT -> stringResource(R.string.home_badge_first_text)
    Badge.FIRST_FAVORITE -> stringResource(R.string.home_badge_first_favorite)
    else -> stringResource(badge.family.label(), badge.threshold)
}

/** Le libellé chiffré d'une famille. La découverte n'y passe jamais : elle n'a pas de seuil. */
private fun Badge.Family.label(): Int = when (this) {
    Badge.Family.STREAK -> R.string.home_badge_streak
    Badge.Family.VOLUME -> R.string.home_badge_volume
    Badge.Family.PERFECT -> R.string.home_badge_perfect
    Badge.Family.DISCOVERY -> R.string.home_badge_streak
}
