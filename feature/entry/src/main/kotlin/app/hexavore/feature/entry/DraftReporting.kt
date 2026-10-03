package app.hexavore.feature.entry

import app.hexavore.domain.ai.AiExchangeLog
import app.hexavore.domain.diary.EntryDraft
import app.hexavore.domain.diary.PhotoBytes
import app.hexavore.domain.diary.PhotoFile
import app.hexavore.domain.usecase.ReportAnalysis
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Ce qu'un signalement rassemble, et où il va le chercher.
 *
 * **Un regroupement et non une couche de plus** : trois sources — le dernier échange, la
 * photo déposée, les lignes affichées — passent ensemble parce qu'elles répondent à une
 * seule question : *qu'est-ce qui était faux*. Passées une par une, elles poussaient le
 * constructeur du `ViewModel` au-delà du seuil de paramètres, et la réponse du projet
 * est de regrouper selon ce que les choses sont plutôt que de relever le seuil.
 *
 * **Les libellés arrivent de l'écran**, comme pour la proposition de plantage : un
 * `Context` ici ferait dépendre d'Android une classe qui n'assemble que du texte, et
 * c'est le genre de dépendance qui se paie au moment de la tester.
 *
 * **Le corps du courriel dit ce que l'écran montrait**, ligne par ligne : c'est la seule
 * chose que les pièces jointes ne disent pas — elles portent ce que le modèle a rendu,
 * pas ce que la résolution en a fait ([D138][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
class DraftReporting @Inject constructor(
    private val reportAnalysis: ReportAnalysis,
    private val exchanges: AiExchangeLog,
    private val photos: PhotoBytes,
) {
    /**
     * @param body le corps, avec un `%1$s` à la place des lignes.
     * @return `false` quand aucune application de messagerie n'a répondu.
     */
    suspend fun report(draft: EntryDraft, photo: PhotoFile?, subject: String, body: String): Boolean {
        // Le dernier echange, et non celui du plat : le depot n'en garde pas la trace,
        // et l'analyse qu'on signale est celle qu'on vient de faire.
        //
        // **`firstOrNull` et non `lastOrNull`** : le journal est rendu du plus recent au
        // plus ancien. Trois signalements ont ainsi joint le *premier* tour d'une
        // analyse profonde -- l'appel au catalogue -- la ou le defaut se voyait au
        // dernier (D142).
        val exchange = runCatching { exchanges.observe().first().firstOrNull() }.getOrNull()
        val bytes = photo?.let { runCatching { photos.of(it) }.getOrNull() }

        return reportAnalysis(
            subject = subject,
            description = body.format(draft.describe()),
            exchange = exchange,
            photo = bytes,
        )
    }
}

/** Les lignes telles que l'ecran les montre : un nom, une quantite, une energie. */
private fun EntryDraft.describe(): String = lines.joinToString(separator = "\n") { line ->
    "- ${line.name} : ${line.quantity} ${line.unit}, ${line.values.kcal ?: "?"} kcal"
}
