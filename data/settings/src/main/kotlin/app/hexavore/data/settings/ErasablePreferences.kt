package app.hexavore.data.settings

import android.content.SharedPreferences
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.backup.StoredPreferences
import app.hexavore.domain.concurrency.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * Tout ce qui est rangé hors de la base, oublié d'un geste.
 *
 * **Deux passes, et chacune répond à un problème distinct.**
 *
 * D'abord les magasins qui portent un flux. `StoredAiCredentials` et
 * `StoredAdjustmentSettings` tiennent un `MutableStateFlow` construit une fois et mis à
 * jour par leurs propres écritures : vider le fichier sous eux les laisserait annoncer
 * une clé qui n'existe plus, jusqu'au prochain lancement. Leur demander d'oublier, en
 * revanche, met le flux d'accord avec le disque.
 *
 * Ensuite les fichiers eux-mêmes. C'est **la passe qui tient la promesse** : elle
 * emporte ce que personne n'a modélisé — le consentement photo, le compteur d'appels,
 * et le réglage qu'on ajoutera l'an prochain sans penser à cette classe. Les magasins
 * ne relisent rien après elle, mais ils n'ont rien à relire : ce qu'ils viennent
 * d'écrire est déjà l'état d'une installation neuve.
 *
 * **Les instances et non leurs noms.** Ce type reçoit les `SharedPreferences` que les
 * modules d'injection ont construites, plutôt que de rouvrir des fichiers par leur nom :
 * un fichier renommé d'un côté et pas de l'autre survivrait à l'effacement en silence,
 * et personne ne s'en apercevrait avant d'avoir promis à quelqu'un que ses clés étaient
 * parties.
 *
 * @see docs/09-donnees-et-sauvegarde.md
 */
internal class ErasablePreferences(
    private val stores: FlowBackedStores,
    private val files: List<SharedPreferences>,
    private val dispatchers: DispatcherProvider,
) : StoredPreferences {
    override suspend fun erase() {
        stores.forgetAll()

        withContext(dispatchers.io) {
            // `commit` et non `apply` : l'ecriture differee ferait repartir l'appelant
            // en annoncant un effacement qui n'a pas encore eu lieu.
            files.forEach { it.edit().clear().commit() }
        }
    }
}

/**
 * Les magasins qui portent un flux, reunis sous le nom de ce qu'ils ont en commun.
 *
 * **Ce n'est pas un sac de dependances.** Ce qui les rassemble est precisement ce qui
 * oblige a les appeler un a un : chacun tient un `MutableStateFlow` construit une fois
 * et mis a jour par ses propres ecritures. Vider leur fichier sous eux ne les
 * previendrait pas, et l'ecran continuerait d'annoncer une cle qui n'existe plus.
 *
 * Le groupe est ne quand le seuil de parametres a mordu, et il dit quelque chose de
 * vrai : un septieme magasin sans flux n'aurait rien a faire ici, et c'est la question
 * qu'on se posera en l'ajoutant.
 */
internal data class FlowBackedStores(
    val credentials: StoredAiCredentials,
    val contribution: StoredContributionSettings,
    val adjustment: StoredAdjustmentSettings,
    val notices: StoredNoticeSettings,
    val debug: StoredDebugSettings,
    val deep: StoredDeepAnalysisSettings,
    val reminders: StoredReminderSettings,
) {
    /** Chacun remet son flux d'accord avec un disque qu'on s'apprete a vider. */
    suspend fun forgetAll() {
        AiProvider.entries.forEach { credentials.forget(it) }
        contribution.forget()
        adjustment.forget()
        notices.forget()
        debug.forget()
        deep.forget()
        reminders.forget()
    }
}
