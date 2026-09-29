package app.hexavore.data.backup

import app.hexavore.domain.backup.SNAPSHOT_FORMAT_VERSION
import app.hexavore.domain.backup.Snapshot
import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.progress.Badge
import app.hexavore.domain.progress.StoredProgress
import java.time.Instant
import java.time.LocalDate

/**
 * La traduction entre l'instantané du domaine et la forme du fichier.
 *
 * **Une énumération inconnue ne fait pas échouer la lecture.** Un fichier écrit par une
 * version qui connaîtrait une septième source de plat doit rester importable : la
 * valeur retombe sur la plus neutre, et les milliers de lignes correctes entrent. C'est
 * le même choix que les mappeurs de Room, et pour la même raison — un import
 * tout-ou-rien perdrait trois ans de journal pour un mot.
 */
internal fun Snapshot.toDto() = SnapshotDto(
    formatVersion = SNAPSHOT_FORMAT_VERSION,
    appVersion = appVersion,
    exportedAt = exportedAt.toString(),
    attribution = ATTRIBUTION,
    profile = profile?.toDto(),
    goals = goals.map { it.toDto() },
    weights = weights.map { WeightDto(date = it.date.toString(), weightKg = it.weightKg) },
    dishes = dishes.map { it.toDto() },
    entries = dishes.flatMap { dish -> dish.entries.map { it.toDto() } },
    foods = foods.map { it.toDto() },
    favorites = favorites.map { it.toDto() },
    adjustment = adjustment.toDto(),
    progress = progress.toDto(),
)

internal fun SnapshotDto.toDomain(): Snapshot {
    val byDish = entries.groupBy { it.dishId }

    return Snapshot(
        exportedAt = exportedAt.toInstantOrEpoch(),
        appVersion = appVersion,
        profile = profile?.toDomain(),
        goals = goals.map { it.toDomain() },
        weights = weights.map { WeightEntry(date = LocalDate.parse(it.date), weightKg = it.weightKg) },
        dishes = dishes.map { it.toDomain(byDish[it.id].orEmpty()) },
        foods = foods.map { it.toDomain() },
        favorites = favorites.map { it.toDomain() },
        adjustment = adjustment.toDomain(),
        progress = progress.toDomain(),
    )
}

/**
 * La progression, dans les deux sens.
 *
 * **Un palier dont le nom ne dit rien est ignoré à la relecture**, comme une source de
 * plat inconnue : un fichier écrit par une version qui connaîtrait un dix-neuvième
 * palier doit rester importable, et perdre une ligne de trophée ne vaut pas de perdre
 * le journal qui va avec.
 */
private fun StoredProgress.toDto() = ProgressDto(
    points = points,
    bestStreak = bestStreak,
    bestPerfectStreak = bestPerfectStreak,
    badges = unlocked.entries.associate { (badge, on) -> badge.name to on.toString() },
)

private fun ProgressDto.toDomain() = StoredProgress(
    points = points,
    bestStreak = bestStreak,
    bestPerfectStreak = bestPerfectStreak,
    unlocked = badges.mapNotNull { (name, on) ->
        val badge = Badge.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
        runCatching { badge to LocalDate.parse(on) }.getOrNull()
    }.toMap(),
)

/**
 * Un instant illisible retombe sur l'époque, plutôt que de faire échouer l'import.
 *
 * Une date d'horodatage corrompue coûte l'ordre d'affichage d'une ligne ; refuser le
 * fichier coûterait tout le journal.
 */
internal fun String.toInstantOrEpoch(): Instant = runCatching { Instant.parse(this) }.getOrDefault(Instant.EPOCH)
