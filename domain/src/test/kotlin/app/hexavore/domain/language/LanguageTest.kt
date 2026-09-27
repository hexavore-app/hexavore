package app.hexavore.domain.language

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * La détection automatique, et le repli.
 *
 * **C'est la seule règle du réglage de langue qui soit une règle**, et elle est ici plutôt
 * que dans un `Context` : les langues du système arrivent en paramètre, exactement comme
 * le thème d'Android arrive en paramètre de `ThemeMode.isDark` ([D113][decisions]). Ce qui
 * s'éprouve donc sans appareil est précisément ce qui décide de ce qu'on lit.
 *
 * **Ce qui n'est pas ici** : que la langue s'applique vraiment. Aucun cas ne monte de
 * ressources, et aucun ne dit que `values-fr/` a été choisi — ça se vérifie en ouvrant
 * l'application, pas autrement ([D129][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
class LanguageTest {
    @Test
    fun `une langue imposee ne regarde pas le telephone`() {
        val impose = LanguageMode.Chosen(ContentLanguage.FRENCH)

        // Meme devant un telephone entierement anglophone : c'est le sens du mot
        // « impose », et c'est la moitie du reglage qui n'existait pas avant.
        assertEquals(ContentLanguage.FRENCH, impose.resolve(listOf("en-US", "en-GB")))
    }

    @Test
    fun `suivre le systeme retient la premiere langue que l application sait ecrire`() {
        val suivre = LanguageMode.System

        assertEquals(ContentLanguage.FRENCH, suivre.resolve(listOf("fr-FR")))
        assertEquals(ContentLanguage.ENGLISH, suivre.resolve(listOf("en-GB")))
    }

    @Test
    fun `la liste entiere est parcourue, dans son ordre`() {
        // Quelqu'un qui regle « allemand, puis francais » a exprime un second choix.
        // Ne lire que la premiere entree lui aurait rendu de l'anglais.
        assertEquals(ContentLanguage.FRENCH, LanguageMode.System.resolve(listOf("de-DE", "fr-FR")))
        assertEquals(ContentLanguage.ENGLISH, LanguageMode.System.resolve(listOf("de-DE", "en-US", "fr-FR")))
    }

    @Test
    fun `une langue inconnue retombe sur l anglais, jamais sur le francais`() {
        // Le repli est ce que le `values/` sans qualificatif porte. Une application qui
        // replierait sur le francais devant un telephone japonais est une application
        // qu'on referme.
        assertEquals(ContentLanguage.ENGLISH, LanguageMode.System.resolve(listOf("ja-JP")))
        assertEquals(ContentLanguage.ENGLISH, LanguageMode.System.resolve(emptyList()))
    }

    @Test
    fun `la region ne compte pas, et les deux ecritures de l etiquette non plus`() {
        // `fr-CA`, `fr_FR` et `FR` designent tous le francais : c'est ce que fait la
        // resolution de ressources d'Android, et s'en ecarter ferait que l'interface et le
        // catalogue ne parlent pas la meme langue sur un telephone quebecois.
        assertEquals(ContentLanguage.FRENCH, ContentLanguage.ofTag("fr-CA"))
        assertEquals(ContentLanguage.FRENCH, ContentLanguage.ofTag("fr_FR"))
        assertEquals(ContentLanguage.FRENCH, ContentLanguage.ofTag("FR"))
        assertEquals(null, ContentLanguage.ofTag("frisian"))
        assertEquals(null, ContentLanguage.ofTag(null))
    }

    @Test
    fun `le code d un reglage survit a un renommage de classe`() {
        // C'est ce qui est ecrit dans les preferences : « system », « en », « fr ». Le nom
        // d'un type Kotlin n'y figure pas, sans quoi renommer une classe changerait la
        // langue de quelqu'un.
        assertEquals("system", LanguageMode.System.code)
        assertEquals("fr", LanguageMode.Chosen(ContentLanguage.FRENCH).code)
        assertEquals(LanguageMode.Chosen(ContentLanguage.ENGLISH), LanguageMode.ofCode("en"))
        assertEquals(LanguageMode.System, LanguageMode.ofCode("system"))
    }

    @Test
    fun `un code illisible se lit comme aucun choix`() {
        // Le fichier de preferences n'est pas controle par l'application seule, et une
        // valeur qu'on ne sait pas lire ne doit pas faire tomber le demarrage.
        assertEquals(LanguageMode.System, LanguageMode.ofCode("klingon"))
        assertEquals(LanguageMode.System, LanguageMode.ofCode(null))
    }

    @Test
    fun `la liste des choix se deduit des langues, elle ne s ecrit pas`() {
        // C'est ce qui fait qu'une troisieme langue apparait toute seule dans les
        // reglages. Un cas qui comparerait a une liste ecrite en dur aurait a etre
        // corrige le jour de cet ajout, ce qui est exactement le contraire du but.
        assertEquals(ContentLanguage.entries.size + 1, LanguageMode.ALL.size)
        assertEquals(LanguageMode.System, LanguageMode.ALL.first())
        assertEquals(
            ContentLanguage.entries.map { LanguageMode.Chosen(it) },
            LanguageMode.ALL.drop(1),
        )
    }
}
