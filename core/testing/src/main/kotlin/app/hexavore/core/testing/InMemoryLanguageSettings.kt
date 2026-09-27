package app.hexavore.core.testing

import app.hexavore.domain.language.LanguageMode
import app.hexavore.domain.language.LanguageSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * La langue réglée, en mémoire.
 *
 * Il part sur [LanguageMode.System], le défaut d'une installation neuve : un faux qui
 * démarrerait sur une langue imposée laisserait passer une détection mal câblée.
 *
 * **[restore] se compte plutôt que d'agir.** Rien à appliquer ici — il n'y a pas de
 * plateforme — mais un cas doit pouvoir affirmer que le démarrage l'a appelée une fois et
 * une seule : c'est la seule chose que ce port promet au lancement.
 */
class InMemoryLanguageSettings(
    initial: LanguageMode = LanguageMode.System,
    /** Un fichier de préférences abîmé : la lecture jette, et l'écran doit tenir. */
    var failure: Boolean = false,
) : LanguageSettings {
    private val mode = MutableStateFlow(initial)

    /** Ce que le magasin porte, pour qu'un cas l'affirme sans passer par un flux. */
    val current: LanguageMode get() = mode.value

    var restored: Int = 0
        private set

    override fun observe(): Flow<LanguageMode> = mode.map {
        if (failure) error("Langue illisible") else it
    }

    override suspend fun choose(mode: LanguageMode) {
        this.mode.value = mode
    }

    override fun restore() {
        restored++
    }
}
