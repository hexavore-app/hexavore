package app.hexavore.core.testing

import app.hexavore.domain.language.ContentLanguage
import app.hexavore.domain.language.ContentLanguages
import app.hexavore.domain.language.SystemLanguages

/**
 * La langue de contenu, posée où l'on veut.
 *
 * **Le français par défaut, alors que l'anglais est le repli de l'application**, et ce
 * n'est pas une étourderie : le catalogue et les décors de test sont écrits en français,
 * et un faux qui rendrait l'anglais ferait échouer cent cas qui ne parlent pas de langue.
 * Les cas qui parlent de langue, eux, la posent.
 *
 * [language] est modifiable pour qu'un test puisse faire basculer la langue sans
 * reconstruire son décor — c'est la situation qui compte le plus, puisqu'un changement de
 * langue ne recrée pas les `ViewModel`.
 */
class FixedLanguage(var language: ContentLanguage = ContentLanguage.FRENCH) : ContentLanguages {
    override fun current(): ContentLanguage = language
}

/**
 * Les langues du système, posées où l'on veut.
 *
 * Une liste et non une langue : ce que la détection automatique doit savoir faire est
 * précisément de parcourir un ordre de préférence, et un faux à une seule valeur ne
 * l'éprouverait jamais.
 */
class FixedSystemLanguages(var tags: List<String> = listOf("fr-FR")) : SystemLanguages {
    override fun tags(): List<String> = tags
}
