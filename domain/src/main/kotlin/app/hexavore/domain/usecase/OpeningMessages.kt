package app.hexavore.domain.usecase

import app.hexavore.domain.opening.OpeningAnswer
import app.hexavore.domain.opening.OpeningContext
import app.hexavore.domain.opening.OpeningLedger
import app.hexavore.domain.opening.OpeningMessage
import app.hexavore.domain.opening.OpeningRecord
import app.hexavore.domain.opening.OpeningRules
import app.hexavore.domain.opening.OpeningSituation
import app.hexavore.domain.opening.OpeningTip
import app.hexavore.domain.opening.TipSettings
import app.hexavore.domain.time.Clock
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant

/**
 * Ce que l'accueil dit en s'ouvrant, **et surtout quand il se tait** ([D179][decisions]).
 *
 * ### Ce que l'auteur a demandé
 *
 * Toutes les quelques ouvertures, une astuce ; et de temps en temps, la demande d'un avis
 * sur le Play Store — une application gratuite se fait connaître par ses notes —, avec de
 * quoi la repousser, dire qu'on l'a déjà fait, ou ne plus jamais la voir.
 *
 * ### Le rythme
 *
 * Une **ouverture** est un retour sur l'accueil après [OpeningRules.ABSENCE] d'absence, ou
 * la toute première. Aucun message avant la [OpeningRules.FIRST_MESSAGE]e, **un au plus
 * par ouverture**, et au moins [OpeningRules.SPACING] ouvertures entre deux. La demande
 * d'avis passe devant l'astuce quand les deux sont dues, parce qu'elle est rare et
 * qu'elle compte davantage ; elle attend une semaine et dix plats. Rien ne paraît
 * pendant le tour guidé, ni à côté d'un rapport de plantage à proposer.
 *
 * ### Ce qui ne se fait pas
 *
 * **Aucune notification système, aucun travail de fond** : ces messages ne paraissent que
 * sur l'accueil, à quelqu'un qui vient de l'ouvrir. Rien ne sort du téléphone ; la fiche
 * du Play Store ne s'ouvre que sous le doigt de celui qui touche une étoile.
 *
 * [decisions]: docs/11-decisions.md
 */
class OpeningMessages(
    private val ledger: OpeningLedger,
    private val tips: TipSettings,
    private val context: OpeningContext,
    private val clock: Clock,
) {
    /**
     * L'accueil vient de paraître.
     *
     * @return le message de cette ouverture, ou `null` — le cas de presque toutes. Un
     *   message rendu est déjà retenu comme montré : une boîte que la mort du processus
     *   emporterait ne reviendra pas, et c'est un message de moins, pas un de trop.
     */
    suspend fun onHomeStarted(): OpeningMessage? {
        val now = clock.now()
        val before = ledger.read()
        if (!before.opensAt(now)) {
            ledger.write(before.copy(lastSeenAt = now))
            return null
        }

        val counted = before.counted(now)
        val message = choose(counted, now)
        ledger.write(message?.let(counted::showing) ?: counted)
        return message
    }

    /** L'accueil vient de s'effacer : c'est de cet instant que la prochaine absence se mesure. */
    suspend fun onHomeStopped() {
        ledger.write(ledger.read().copy(lastSeenAt = clock.now()))
    }

    /** Ce qu'on a répondu au message de cette ouverture. */
    suspend fun answer(answer: OpeningAnswer) {
        when (answer) {
            OpeningAnswer.SEEN -> Unit
            OpeningAnswer.NO_MORE_TIPS -> tips.setEnabled(false)
            OpeningAnswer.RATED, OpeningAnswer.ALREADY_RATED, OpeningAnswer.NEVER ->
                ledger.write(ledger.read().copy(ratingClosed = true))

            OpeningAnswer.LATER -> {
                val record = ledger.read()
                ledger.write(record.copy(ratingAfter = record.openings + OpeningRules.RATING_SNOOZE))
            }
        }
    }

    /**
     * Le message de cette ouverture, s'il en est un de dû.
     *
     * La situation n'est relue que si un message est dû : c'est rarement le cas, et elle
     * demande quatre ports.
     */
    private suspend fun choose(record: OpeningRecord, now: Instant): OpeningMessage? {
        val situation = if (record.dueForMessage()) context.current() else null
        return situation?.takeIf { it.quiet }?.let { rating(record, it, now) ?: tip(record, it) }
    }

    private fun rating(record: OpeningRecord, situation: OpeningSituation, now: Instant): OpeningMessage? {
        val since = record.firstOpenedAt ?: return null
        val due = !record.ratingClosed &&
            record.openings >= record.ratingAfter &&
            situation.dishes >= OpeningRules.RATING_AFTER_DISHES &&
            Duration.between(since, now) >= OpeningRules.RATING_AFTER
        return OpeningMessage.Rating.takeIf { due }
    }

    private suspend fun tip(record: OpeningRecord, situation: OpeningSituation): OpeningMessage? {
        if (!tips.observe().first()) return null
        return OpeningTip.entries
            .firstOrNull { it !in record.tipsShown && it.audience.includes(situation.aiConfigured) }
            ?.let(OpeningMessage::Tip)
    }
}

/**
 * Est-ce une ouverture ? La toute première, ou un retour après une absence assez longue.
 *
 * Hors de la classe, comme les trois suivantes : ce sont des règles sur le registre, pas
 * des gestes du cas d'usage, et le seuil de fonctions n'a pas à les compter.
 */
private fun OpeningRecord.opensAt(now: Instant): Boolean =
    lastSeenAt == null || Duration.between(lastSeenAt, now) >= OpeningRules.ABSENCE

/** Le registre d'une ouverture de plus. */
private fun OpeningRecord.counted(now: Instant): OpeningRecord =
    copy(openings = openings + 1, lastSeenAt = now, firstOpenedAt = firstOpenedAt ?: now)

/** Assez d'ouvertures depuis l'installation, et depuis le dernier message. */
private fun OpeningRecord.dueForMessage(): Boolean = openings >= OpeningRules.FIRST_MESSAGE &&
    (lastMessageAt == null || openings - lastMessageAt >= OpeningRules.SPACING)

/** Le registre d'une ouverture qui montre ce message. */
private fun OpeningRecord.showing(message: OpeningMessage): OpeningRecord = copy(
    lastMessageAt = openings,
    tipsShown = if (message is OpeningMessage.Tip) tipsShown + message.tip else tipsShown,
)
