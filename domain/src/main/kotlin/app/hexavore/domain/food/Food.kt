package app.hexavore.domain.food

import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.NutrientValues
import java.time.Instant

/** Identifiant d'une fiche d'aliment. UUIDv4 généré côté application. */
@JvmInline
value class FoodId(val value: String)

/**
 * D'où vient une fiche.
 *
 * Une énumération et non un booléen « personnel ou pas » : les trois provenances se
 * comportent différemment. Un aliment CIQUAL ne se modifie pas, un produit Open Food
 * Facts se rafraîchit, un aliment personnel se supprime.
 */
enum class FoodSource {
    /** Table de l'ANSES, embarquée. Copiée dans le catalogue au premier usage. */
    CIQUAL,

    /** Produit récupéré par code-barres, mis en cache définitivement. */
    OFF,

    /** Créé par l'utilisateur. */
    CUSTOM,
}

/**
 * Une portion nommée : « 1 pomme moyenne », « 1 tranche ».
 *
 * Elle appartient à une fiche, et c'est pour ça qu'elle n'existait pas avant cette
 * tranche : demander à l'utilisateur de définir lui-même ce que pèse une tranche
 * est exactement le travail qu'on veut lui épargner ([D42][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
data class FoodServing(val label: String, val grams: Double, val isDefault: Boolean = false)

/**
 * Une fiche d'aliment : ce qu'on réutilise d'une saisie à l'autre.
 *
 * **Elle ne décide de rien dans le journal.** Une entrée fige ses macros à
 * l'enregistrement et ne relit jamais cette fiche ([D05][decisions]) : la modifier
 * ne peut pas réécrire le passé, et la supprimer ne peut pas l'amputer. Le lien
 * `FoodEntry.foodId` sert à la provenance et au ré-ajout.
 *
 * [per100g] porte six valeurs dont l'énergie peut manquer — voir [NutrientValues].
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/07-modele-de-donnees.md
 */
data class Food(
    val id: FoodId,
    val source: FoodSource,
    /** Code CIQUAL ou code-barres. `null` pour un aliment personnel sans origine. */
    val sourceRef: String? = null,
    val name: String,
    /**
     * Le libellé raccourci, quand la fiche en a un.
     *
     * **Un affichage, et rien d'autre.** [name] ne bouge jamais : c'est lui qui relie
     * la fiche à sa source, et c'est sur lui que la recherche compare — un index bâti
     * sur un titre court ne trouverait plus « poulet cuit au four sans matière grasse ».
     *
     * Il ne vit pas dans la table du catalogue : pour une fiche de l'ANSES, il se
     * relit dans la base de référence par son code, comme le rayon et les portions.
     * Une copie figerait le titre du jour où elle a été faite, et le corriger
     * n'atteindrait jamais les fiches déjà utilisées.
     *
     * `null` veut dire « le libellé se lit très bien tel quel » : c'est le cas des
     * deux cinquièmes de la table, des produits scannés et de tout aliment personnel,
     * que l'utilisateur a nommé lui-même.
     */
    val shortName: String? = null,
    /**
     * Les noms sous lesquels on demande cette fiche, **normalisés** — vide pour presque
     * toutes.
     *
     * Le classement suppose que la fiche générique porte le nom le plus court de sa
     * famille, et sur la table de l'ANSES c'est souvent faux : « Riz blanc, cru » est
     * plus court que le riz cuit, « Café, moulu » que le café qu'on boit. Une table relue
     * à la main donne « riz » au riz cuit et « café » au café prêt à boire, et
     * `FoodRanking` pèse ces noms **comme le libellé** : un nom courant est un nom, pas
     * une règle à côté ([D164][decisions]).
     *
     * Une propriété de la **référence**, relue par code comme [shortName] : vide pour un
     * aliment personnel ou un produit scanné, que personne d'autre n'a nommé. Jamais
     * affiché, et jamais figé dans une copie.
     *
     * [decisions]: docs/11-decisions.md
     */
    val commonNames: Set<String> = emptySet(),
    val brand: String? = null,
    /**
     * Le rayon, quand la fiche en a un.
     *
     * `null` pour un aliment personnel, pour un produit scanné, et pour les lignes de
     * l'ANSES qui n'entrent dans aucune des huit cases du bandeau — voir
     * [FoodCategory]. Ce n'est pas un trou à combler : « ne pas avoir de rayon » est
     * une réponse, et la seule honnête pour une huile ou une soupe.
     */
    val category: FoodCategory? = null,
    val per100g: NutrientValues,
    /**
     * Celles des six teneurs qui **viennent d'un modèle** et non d'une mesure.
     *
     * CIQUAL laisse des trous — 313 fiches sur 3 484 en ont au moins un — et un
     * petit modèle peut les combler. Mais **une valeur complétée reste une valeur
     * inventée**, et la fiche doit le porter valeur par valeur : une fiche dont trois
     * teneurs sur six viennent d'un modèle n'est ni une fiche mesurée ni une
     * estimation, et un drapeau unique aurait menti dans les deux sens.
     *
     * C'est [D83][decisions] poussé d'un cran : là, une estimation ne devenait jamais
     * une fiche ; ici elle entre au catalogue. Et c'est le même vocabulaire que
     * `DraftLine.edited`, qui dit déjà quelles valeurs d'une ligne n'ont pas été
     * calculées — un `Set<Macro>` plutôt que six booléens, parce que la question se
     * pose de la même façon pour les six.
     *
     * **La valeur d'origine n'est jamais écrasée.** Elle et la complétion ne se
     * rangent pas au même endroit : sans cela, un nouvel import de la table de
     * l'ANSES écraserait les complétions — ou pire, les prendrait pour des mesures.
     * [per100g] porte ce qui s'affiche, et ce champ dit d'où chaque valeur vient.
     *
     * [decisions]: docs/11-decisions.md
     */
    val estimated: Set<Macro> = emptySet(),
    val servings: List<FoodServing> = emptyList(),
    /** Quantité proposée à l'ouverture. `null` vaut 100 g. */
    val defaultServingG: Double? = null,
    /**
     * `true` pour une boisson, `false` pour un solide, **`null` quand on ne sait pas**.
     *
     * Trois états et non deux, pour la même raison que les six teneurs : `false`
     * affirmerait qu'on a regardé. Rien ne le dit d'un aliment de la table de l'ANSES,
     * et un produit scanné ne le dit que s'il déclare une portion en millilitres.
     *
     * Cette information n'est connaissable qu'**au moment où la fiche est récupérée**,
     * et c'est pourquoi elle est retenue avant d'avoir un lecteur : la reconstituer
     * plus tard demanderait de réinterroger Open Food Facts pour chaque produit déjà
     * en cache. Le résolveur de la tranche 6 la lira, avec la densité.
     */
    val isLiquid: Boolean? = null,
    /**
     * Quand la fiche a été récupérée d'Open Food Facts. `null` pour les deux autres
     * provenances, qui ne se périment pas.
     *
     * Elle date le **cache**, et [docs/04][sources] en fait la condition du
     * rafraîchissement proposé au-delà de quatre-vingt-dix jours. Ce rafraîchissement
     * n'existe pas encore ; la date, elle, est écrite dès maintenant, parce qu'un
     * instant qu'on n'a pas noté ne se retrouve pas — toutes les fiches mises en cache
     * d'ici là seraient sans âge, et donc indistinguables d'une fiche d'hier.
     *
     * [sources]: docs/04-sources-de-donnees.md
     */
    val fetchedAt: Instant? = null,
    /** `null` tant que l'aliment n'a jamais servi. C'est ce que « Récents » filtre. */
    val lastUsedAt: Instant? = null,
    val useCount: Int = 0,
    val favorite: Boolean = false,
) {
    /** La portion proposée par défaut, s'il y en a une. */
    val defaultServing: FoodServing? get() = servings.firstOrNull { it.isDefault }

    /**
     * Le nom sous lequel cette fiche se montre : le titre court s'il existe, sinon
     * le libellé.
     *
     * Un seul endroit décide, et c'est ce qui compte : quatre listes, l'écran de
     * validation et le nom que prend une nouvelle ligne de journal posent la même
     * question, et six réponses divergeraient au premier oubli. C'est aussi ce nom
     * que `FoodEntry.displayName` fige à l'enregistrement — le journal garde donc ce
     * qui était affiché le jour où on l'a écrit ([D05][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    val displayName: String get() = shortName ?: name

    /**
     * Ce qu'une fiche modifiable a de particulier.
     *
     * Seul un aliment personnel se modifie et se supprime. Un aliment CIQUAL est une
     * référence publiée ; un produit Open Food Facts est un cache qui se rafraîchit.
     */
    val editable: Boolean get() = source == FoodSource.CUSTOM
}
