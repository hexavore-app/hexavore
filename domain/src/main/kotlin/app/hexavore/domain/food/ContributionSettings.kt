package app.hexavore.domain.food

import kotlinx.coroutines.flow.Flow

/**
 * Ce que l'utilisateur a réglé pour contribuer : son compte, et où l'on envoie.
 *
 * **Un port distinct de [FoodContributionTarget]**, parce que ce ne sont pas les mêmes
 * appelants : l'écran de réglages écrit ici et n'envoie jamais rien ; l'écran d'une
 * fiche envoie et n'écrit jamais de compte. Les fondre obligerait chacun à dépendre de
 * ce que l'autre fait ([docs/06][architecture] § I).
 *
 * [architecture]: docs/06-architecture.md
 */
interface ContributionSettings {
    /**
     * Le réglage courant, et ses changements.
     *
     * Un flux et non une lecture unique : l'écran affiche ce qu'il vient d'écrire, et
     * un instantané l'obligerait à relire après chaque geste — le même choix que pour
     * les clés d'IA, et pour la même raison.
     */
    fun observe(): Flow<ContributionSetup>

    /** Range le compte. Le mot de passe est chiffré ; l'identifiant ne l'est pas. */
    suspend fun save(account: OffAccount)

    /** Oublie le compte. Le bouton de contribution disparaît avec lui. */
    suspend fun forget()

    /**
     * Bascule la cible entre la vraie base et l'instance de test.
     *
     * Un réglage et non une variante de compilation : c'est en le basculant qu'on
     * vérifie qu'un envoi aboutit vraiment, et une `buildConfigField` demanderait de
     * réinstaller l'application pour ça ([D90][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    suspend fun useSandbox(sandbox: Boolean)
}

/**
 * L'état des réglages de contribution.
 *
 * [sandbox] est **éteint par défaut**, et c'est le bon défaut malgré la prudence qu'on
 * pourrait vouloir : une contribution qui part par défaut vers un bac à sable serait
 * une contribution qui n'existe pas, offerte à quelqu'un qui croit contribuer.
 */
data class ContributionSetup(val account: OffAccount? = null, val sandbox: Boolean = false) {
    /**
     * `true` quand une contribution est possible.
     *
     * Sans compte, le bouton **n'apparaît pas** plutôt que de refuser : c'est la règle
     * des modes d'IA sans clé ([docs/02][parcours]), et un bouton qui explique pourquoi
     * il ne marche pas encombre l'écran d'une fiche qu'on est venu lire.
     *
     * [parcours]: docs/02-parcours-et-ecrans.md
     */
    val open: Boolean get() = account?.usable == true
}

/**
 * La contribution à Open Food Facts est-elle proposée dans cette version ?
 *
 * **Non, et c'est une mise en réserve, pas une suppression** ([D158][decisions]). Le
 * code existe et passe ses tests — le client, la proposition, le compte chiffré —, mais
 * aucun envoi réel n'a encore atteint la vraie base, et c'est la seule écriture
 * **publique** de l'application : une fiche partie sous le compte de quelqu'un ne se
 * reprend pas. Elle ne sort donc pas avant d'avoir été éprouvée, pour la raison qui
 * tient quatre fournisseurs d'IA en réserve.
 *
 * **Un drapeau plutôt qu'un retrait** : le jour où la contribution est éprouvée, elle
 * revient en changeant un mot — la section des réglages réapparaît, la proposition qui
 * suit la création d'une fiche aussi. D'ici là, un compte saisi par un testeur reste
 * rangé et chiffré, et « Tout effacer » l'emporte comme avant.
 *
 * [decisions]: docs/11-decisions.md
 */
const val CONTRIBUTION_OFFERED: Boolean = false
