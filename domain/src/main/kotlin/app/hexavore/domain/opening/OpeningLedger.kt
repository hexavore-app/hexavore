package app.hexavore.domain.opening

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Ce que l'application retient de ses ouvertures, pour espacer ce qu'elle y dit
 * ([D179][decisions]).
 *
 * **Rien de personnel, et rien qui sorte.** Un compte, deux instants, les astuces déjà
 * vues, et ce qu'on a répondu à la demande d'avis : de quoi ne pas répéter, ni insister.
 * Le fichier est celui des pastilles et du tour — l'état de ce que l'écran a déjà dit —,
 * et un effacement complet le vide avec le reste.
 *
 * [decisions]: docs/11-decisions.md
 */
interface OpeningLedger {
    suspend fun read(): OpeningRecord

    suspend fun write(record: OpeningRecord)
}

/**
 * Le registre, tel qu'il est lu et réécrit à chaque passage de l'accueil.
 *
 * @property openings combien d'ouvertures ont été comptées ([OpeningRules.ABSENCE]).
 * @property lastSeenAt la dernière fois que l'accueil a paru ou s'est effacé : c'est
 *   d'elle que se mesure l'absence qui fait une ouverture.
 * @property firstOpenedAt la première ouverture comptée. La demande d'avis attend une
 *   semaine à partir d'elle — et, pour qui installait une version d'avant, à partir de
 *   la première ouverture qui suit la mise à jour.
 * @property lastMessageAt l'ouverture où le dernier message a paru.
 * @property tipsShown les astuces déjà montrées : chacune ne paraît qu'une fois.
 * @property ratingClosed vrai quand la demande d'avis ne reviendra plus.
 * @property ratingAfter l'ouverture avant laquelle elle ne revient pas, après « Plus tard ».
 */
data class OpeningRecord(
    val openings: Int = 0,
    val lastSeenAt: Instant? = null,
    val firstOpenedAt: Instant? = null,
    val lastMessageAt: Int? = null,
    val tipsShown: Set<OpeningTip> = emptySet(),
    val ratingClosed: Boolean = false,
    val ratingAfter: Int = 0,
)

/**
 * Les astuces sont-elles allumées ?
 *
 * **Un réglage à part du registre**, parce qu'il se change de deux endroits — la boîte
 * d'une astuce, et l'écran des notifications — quand le registre ne s'écrit que depuis
 * l'accueil. Allumées par défaut : une astuce éteinte d'office ne serait découverte par
 * personne.
 */
interface TipSettings {
    fun observe(): Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean)
}
