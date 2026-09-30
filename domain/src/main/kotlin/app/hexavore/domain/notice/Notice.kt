package app.hexavore.domain.notice

import kotlinx.coroutines.flow.Flow

/**
 * Ce que l'application a remarqué et qui mérite une pastille.
 *
 * ### Ce qu'une pastille est, et ce qu'elle n'est pas
 *
 * **Elle désigne quelque chose à faire, jamais quelque chose à lire.** Une pastille
 * qu'on éteint en la regardant est une notification déguisée ; celles-ci s'éteignent
 * parce que la situation a changé — une clé est branchée, une pesée est notée, un repas
 * oublié est rattrapé. Aucune ne se rejette : il n'y a pas de bouton pour dire « je
 * sais », parce qu'un tel bouton transformerait chaque règle en rappel, et un rappel
 * qu'on peut faire taire ne dit plus rien à personne.
 *
 * **Rien ne sort de l'application.** Pas de notification système, pas de permission
 * demandée, pas de travail de fond : ce sont des points colorés que l'on voit en
 * ouvrant l'écran, et rien d'autre. Le jour où l'une d'elles doit sonner sur l'écran de
 * verrouillage, ce sera une autre décision, avec un autre coût.
 *
 * **Chacune s'éteint séparément** ([NoticeSettings]). Un interrupteur unique aurait
 * obligé à renoncer à celle du poids pour se débarrasser de celle de l'IA.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
enum class Notice {
    /**
     * Aucun fournisseur d'IA ne sert.
     *
     * C'est la capacité la plus visible que l'application n'exploite pas : les deux
     * boutons sont là, grisés, et rien ne dit qu'il suffit d'une clé.
     */
    AI_NOT_CONFIGURED,

    /**
     * Le dernier appel d'analyse a été refusé pour la clé.
     *
     * **Le défaut silencieux par excellence** : l'analyse cesse de fonctionner, chaque
     * tentative échoue de la même façon, et rien sur l'accueil ne dit que la cause est
     * une clé expirée ou révoquée plutôt qu'une panne passagère.
     */
    AI_KEY_REJECTED,

    /**
     * Aucune pesée depuis [WEIGHT_SILENCE_DAYS] jours.
     *
     * Sans pesées régulières, la moyenne mobile se tait et l'adaptation hebdomadaire ne
     * peut rien proposer. **La fonction s'éteint sans rien dire** — c'est exactement le
     * genre de silence qu'une pastille sert à rompre.
     */
    WEIGHT_STALE,

    /**
     * Hier n'a aucune ligne, **et l'avant-veille en avait**.
     *
     * Rattraper un repas oublié est possible depuis que l'accueil porte une date ;
     * encore faut-il remarquer qu'il manque. La pastille se pose sur la journée
     * concernée, et non sur une icône : c'est là qu'on la touche pour agir.
     *
     * **Les deux moitiés de la règle comptent.** « Hier est vide » seul s'allumait chez
     * quelqu'un qui n'a jamais rien noté, et tous les jours chez celui qui a cessé : un
     * oubli suppose une habitude, et l'avant-veille est ce qui l'atteste
     * ([D135][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    YESTERDAY_EMPTY,
}

/**
 * Le nombre de jours de silence après lequel l'absence de pesée se signale.
 *
 * **Sept et non trois** : la moyenne mobile travaille sur une fenêtre de sept jours, et
 * se plaindre plus tôt reviendrait à réclamer une pesée dont le calcul n'a pas encore
 * besoin. Sept et non trente : au bout d'un mois, la tendance affichée décrit un état
 * qui n'existe plus.
 */
const val WEIGHT_SILENCE_DAYS = 7L

/**
 * Lesquelles sont allumées.
 *
 * **Toutes par défaut.** Une pastille éteinte d'office ne serait découverte par
 * personne, et le réglage existe pour celui que l'une d'elles agace — pas pour lui
 * cacher les trois autres.
 */
interface NoticeSettings {
    fun observe(): Flow<Set<Notice>>

    suspend fun setEnabled(notice: Notice, enabled: Boolean)
}

/**
 * Le souvenir d'une clé refusée.
 *
 * **Un port à part et non une entrée du journal d'usage** : celui-ci compte ce qui a
 * été consommé, c'est-à-dire ce qui a réussi. Un échec d'authentification n'est pas une
 * consommation, il n'a ni jetons ni modèle à additionner — et le ranger là obligerait à
 * inventer des zéros pour les colonnes qui ne s'appliquent pas.
 *
 * Il se retient et s'oublie tout seul : une analyse qui aboutit l'efface, une clé qu'on
 * enregistre aussi. Rien à rejeter à la main.
 */
interface KeyRejection {
    fun observe(): Flow<Boolean>

    /** Le fournisseur vient de refuser la clé. */
    suspend fun note()

    /** Une analyse a abouti, ou une clé neuve vient d'être donnée. */
    suspend fun clear()
}
