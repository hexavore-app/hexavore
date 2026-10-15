package app.hexavore.integration.ai

/**
 * Ce qui remplace `temperature = 0.2`, et le levier qu'on déplacera.
 *
 * `low` est la lecture la plus proche de l'intention de [docs/05][ia] — une tâche de
 * perception n'a pas besoin de délibérer — et la moins chère, ce qui compte quand
 * c'est l'utilisateur qui paie. **Il n'a été calibré contre rien** : aucune
 * reconnaissance réelle n'a encore tourné. Une constante nommée, pour qu'un jour de
 * calibrage n'ait qu'un mot à changer.
 *
 * [ia]: docs/05-ia.md
 */
private const val EFFORT = "low"

/**
 * L'effort, ou rien pour un modèle qui n'en connaît pas ([D162][decisions]).
 *
 * **Claude Haiku 4.5 n'a pas de paramètre `effort`**, et la liste le proposait : chaque
 * analyse rendait un `400`. Sonnet 4.5, et les Opus d'avant la 4.6 — où il n'existait que
 * derrière un en-tête bêta qu'on n'envoie pas —, pas davantage. La liste ne les propose
 * plus, mais un identifiant tapé à la main, ou choisi avant cette version, peut encore
 * en désigner un.
 *
 * **Une liste de ce qui le refuse, et non de ce qui l'accepte** : un modèle qui sortira
 * demain l'acceptera, comme tous ceux d'aujourd'hui, et une liste d'acceptation le
 * priverait d'effort jusqu'à la prochaine version de l'application.
 *
 * Sorti de `AnthropicRecognizer.kt`, qui atteignait le seuil de fonctions par fichier :
 * c'est une règle sur l'identifiant du modèle, pas un geste du client.
 *
 * [decisions]: docs/11-decisions.md
 */
internal fun effortFor(model: String): String? = EFFORT.takeUnless { NO_EFFORT.any(model::startsWith) }

/** Les préfixes des modèles sans `effort`, identifiants datés compris (`claude-opus-4-20250514`). */
private val NO_EFFORT = listOf(
    "claude-3",
    "claude-haiku-4",
    "claude-sonnet-4-0",
    "claude-sonnet-4-2",
    "claude-sonnet-4-5",
    "claude-opus-4-0",
    "claude-opus-4-1",
    "claude-opus-4-2",
    "claude-opus-4-5",
)
