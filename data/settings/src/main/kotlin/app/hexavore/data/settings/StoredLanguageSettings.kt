package app.hexavore.data.settings

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.language.ContentLanguage
import app.hexavore.domain.language.ContentLanguages
import app.hexavore.domain.language.LanguageMode
import app.hexavore.domain.language.LanguageSettings
import app.hexavore.domain.language.SystemLanguages
import app.hexavore.domain.language.resolve
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withContext

/**
 * La langue, telle qu'elle est réglée et telle qu'elle s'applique.
 *
 * ### Pourquoi la plateforme détient la réponse
 *
 * C'est elle qui choisit un `values-fr/`, pas nous. Et depuis Android 13, c'est elle qui
 * **retient** la langue d'une application et l'expose dans ses propres réglages : il y a
 * donc deux portes vers le même réglage, la nôtre et la sienne. Lire notre fichier de
 * préférences comme vérité aurait laissé l'interface en français et le catalogue en
 * anglais le jour où quelqu'un passe par la porte d'Android.
 *
 * La lecture interroge donc la plateforme, et [applied] est le seul endroit qui sache
 * laquelle interroger.
 *
 * ### Deux lectures, et un test de version assumé
 *
 * **À partir d'Android 13, `LocaleManager` est demandé directement.** C'est lui qui détient
 * la langue de l'application, y compris celle qu'on a choisie dans les réglages d'Android,
 * y compris avant qu'une seule activité existe. `AppCompatDelegate.getApplicationLocales`
 * y délègue — mais il a besoin pour cela d'un `Context` qu'il prend sur une activité
 * vivante, et [restore] est appelée quand il n'y en a aucune. Là, il rendrait une liste
 * vide qui veut dire « je ne sais pas encore » et non « aucune langue », et on écraserait
 * un choix fait dans les réglages d'Android à chaque lancement.
 *
 * **En deçà, le délégué d'AppCompat est la seule mémoire qui existe**, et elle ne dure que
 * le temps du processus : c'est ce que [restore] rattrape.
 *
 * Ce `Build.VERSION` est donc une **description** et non un pari : il dit lequel des deux
 * mécanismes détient la réponse, ce qui dépend en effet de la version d'Android.
 *
 * ### Ce que nos préférences servent encore
 *
 * **Un cahier de rappel, et rien d'autre.** En deçà d'Android 13, aucune couche ne
 * réapplique une langue imposée au démarrage suivant. Au-delà, la plateforme l'a déjà fait
 * et [restore] ne touche à rien.
 *
 * L'écriture, elle, passe toujours par `AppCompatDelegate.setApplicationLocales` : c'est
 * l'API documentée, et elle délègue au framework là où il existe.
 */
internal class StoredLanguageSettings(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val dispatchers: DispatcherProvider,
    private val systemLanguages: SystemLanguages,
) : LanguageSettings,
    ContentLanguages {
    private val current = MutableStateFlow(applied())

    /**
     * Relu à chaque abonnement.
     *
     * Ouvrir l'écran d'apparence relit donc la vérité, ce qui suffit à afficher le bon
     * bouton radio après un passage par les réglages d'Android. Un flux qui ne porterait
     * que nos écritures aurait montré « suivre le système » à quelqu'un qui vient de
     * choisir le français ailleurs.
     */
    override fun observe(): Flow<LanguageMode> = current.onSubscription { current.value = applied() }

    override suspend fun choose(mode: LanguageMode) = withContext(dispatchers.io) {
        // Note d'abord, applique ensuite : appliquer recree les activites, et une ecriture
        // qui suivrait une recreation pourrait ne jamais avoir lieu.
        preferences.edit { putString(LANGUAGE_MODE, mode.code) }
        current.value = mode
        AppCompatDelegate.setApplicationLocales(mode.toLocaleList())
    }

    /**
     * Ce que la plateforme n'a pas restauré, on le restaure.
     *
     * À partir d'Android 13, [applied] rend déjà ce que `LocaleManager` retient, donc il n'y
     * a rien à faire — et surtout rien à écraser. En deçà, il rend « suivre le système »
     * parce que le processus vient de naître, et c'est là que le cahier de rappel sert.
     */
    override fun restore() {
        if (applied() is LanguageMode.Chosen) return
        val noted = LanguageMode.ofCode(preferences.getString(LANGUAGE_MODE, null))
        if (noted is LanguageMode.Chosen) AppCompatDelegate.setApplicationLocales(noted.toLocaleList())
    }

    /**
     * La langue en vigueur, recalculée à chaque appel.
     *
     * Les deux lectures sont en mémoire — la langue de l'application et une `Configuration`
     * déjà chargée — donc rien ne justifie de mettre le résultat en cache. Et le mettre en
     * cache serait faux : quelqu'un qui change la langue de son téléphone pendant que
     * l'application vit ne redémarre pas le processus.
     */
    override fun current(): ContentLanguage = applied().resolve(systemLanguages.tags())

    /**
     * Le réglage tel que la plateforme l'applique.
     *
     * Une liste vide veut dire « suivre le système », c'est-à-dire exactement ce que
     * [LanguageMode.System] désigne. Une langue qu'on ne sait pas écrire — une liste
     * héritée d'une version plus récente de l'application — se lit aussi comme « suivre le
     * système » : c'est la lecture la plus prudente, et [LanguageMode.ofCode] la fait déjà
     * pour le fichier de préférences.
     */
    private fun applied(): LanguageMode {
        // `ContentLanguage.ofTag` et non une comparaison de codes : la plateforme rend une
        // etiquette complete -- `fr-FR` sur un telephone francais -- et c'est la regle du
        // domaine qui sait n'en garder que la langue.
        return ContentLanguage.ofTag(appliedTag())?.let(LanguageMode::Chosen) ?: LanguageMode.System
    }

    private fun appliedTag(): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java)?.applicationLocales?.get(0)?.toLanguageTag()
    } else {
        AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag()
    }
}

/**
 * Le réglage, dans la forme qu'attend la plateforme.
 *
 * « Suivre le système » est une **liste vide** et non l'absence d'appel : c'est ainsi
 * qu'on retire une langue imposée, et un `setApplicationLocales` qu'on n'appelle pas
 * laisserait la précédente en place.
 */
private fun LanguageMode.toLocaleList(): LocaleListCompat = when (this) {
    LanguageMode.System -> LocaleListCompat.getEmptyLocaleList()
    is LanguageMode.Chosen -> LocaleListCompat.forLanguageTags(language.tag)
}

private const val LANGUAGE_MODE = "appearance.language_mode"
