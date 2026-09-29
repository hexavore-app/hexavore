package app.hexavore.integration.reminders

import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Combien de temps avant la prochaine sonnerie.
 *
 * **La seule chose de ce module qui s'éprouve sans Android**, et c'est pourquoi elle
 * est ici plutôt que dans le planificateur : une règle de temps mêlée à un appel à
 * `WorkManager` ne se vérifie qu'en attendant l'heure qu'elle décrit.
 *
 * **Demain quand l'heure est passée**, aujourd'hui sinon. La borne est stricte : un
 * rappel replanifié à la seconde où il vient de sonner doit viser le lendemain, sans
 * quoi il sonnerait deux fois.
 *
 * **Le calcul passe par le fuseau, jamais par une arithmétique d'heures.** Deux fois
 * par an, un jour fait vingt-trois ou vingt-cinq heures : ajouter vingt-quatre heures à
 * la dernière sonnerie décalerait le rappel d'une heure, puis d'une autre au changement
 * suivant, et personne ne ferait le lien.
 */
internal fun delayUntil(target: LocalTime, from: ZonedDateTime, zone: ZoneId): Duration {
    val today = from.withZoneSameInstant(zone).toLocalDate().atTime(target).atZone(zone)
    val next = if (today.isAfter(from)) today else today.plusDays(1)
    return Duration.between(from, next)
}
