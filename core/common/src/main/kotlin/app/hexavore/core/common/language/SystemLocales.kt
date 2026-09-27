package app.hexavore.core.common.language

import android.content.res.Resources
import app.hexavore.domain.language.SystemLanguages
import javax.inject.Inject

/**
 * Les langues que le propriétaire de l'appareil a réglées, dans son ordre.
 *
 * **`Resources.getSystem()` et non le `Context` de l'application.** C'est la seule
 * lecture qui donne les langues du **système** : celles de l'application portent, depuis
 * Android 13, la langue imposée en tête de liste. Les lire là ferait que « suivre le
 * système » suive le dernier choix imposé, et qu'on ne puisse plus jamais revenir en
 * arrière — le réglage aurait l'air de fonctionner, et se serait verrouillé.
 *
 * **Relu à chaque appel.** Quelqu'un qui change la langue de son téléphone ne redémarre
 * pas le processus de l'application ; une liste mise en cache au démarrage continuerait
 * de répondre l'ancienne, exactement comme le fuseau horaire de `SystemClock`.
 *
 * Les étiquettes sont rendues en BCP 47 — `fr-FR`, `en-US` — et c'est
 * [app.hexavore.domain.language.ContentLanguage.ofTag] qui n'en garde que la langue :
 * la lecture ne décide de rien, la règle est dans le domaine.
 */
class SystemLocales @Inject constructor() : SystemLanguages {
    override fun tags(): List<String> {
        val locales = Resources.getSystem().configuration.locales
        return List(locales.size()) { index -> locales[index].toLanguageTag() }
    }
}
