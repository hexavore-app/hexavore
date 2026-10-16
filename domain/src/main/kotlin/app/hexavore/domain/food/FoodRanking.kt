package app.hexavore.domain.food

/**
 * L'ordre dans lequel les résultats se présentent.
 *
 * Sans lui, « pomme » renvoie « Pomme de terre à chair farineuse, crue » avant
 * « Pomme, chair et peau, crue » — ce que [docs/04][sources] signale comme le défaut
 * à éviter, parce qu'il rend la recherche inutilisable sur les mots les plus courants.
 *
 * BM25 n'est pas disponible : c'est une fonction de FTS5, que `minSdk 26` interdit
 * ([D49][decisions]). Son absence coûte peu ici. Sur 3 484 libellés courts, la
 * pondération par fréquence de terme départage mal — « pomme » apparaît une fois
 * dans chacun des deux candidats ci-dessus — alors que la **longueur du nom** et
 * l'**usage réel** les départagent nettement. C'était déjà le critère décisif quand
 * BM25 était prévu, et il devait de toute façon se calculer ici.
 *
 * Fonction pure, testée pour elle-même : c'est la seule partie de la recherche dont
 * la justesse ne se voit pas à l'œil sur un écran.
 *
 * **Elle vit dans `:domain` et sert deux appelants.** L'écran de recherche s'en sert
 * pour trier ; le résolveur de la tranche 6 s'en sert pour décider s'il remplit une
 * ligne sans rien demander. Ce sont deux questions différentes, mais **un seul
 * score** : deux règles concurrentes sur le même couple (nom, requête) auraient fini
 * par ne plus être d'accord, et l'utilisateur aurait vu un candidat classé premier
 * que le résolveur refuse ([D74][decisions]). Le même raisonnement que `FoodFilter`,
 * qui est une règle de ce que l'utilisateur voit et se teste donc sur la JVM.
 *
 * [sources]: docs/04-sources-de-donnees.md
 * [decisions]: docs/11-decisions.md
 */
object FoodRanking {
    /**
     * Le score est calculé **une fois par aliment**, puis trié.
     *
     * Le comparateur le recalculait à chaque comparaison, donc une vingtaine de fois par
     * aliment, avec une normalisation Unicode à chaque fois. Sur trente lignes cela ne
     * se voyait pas ; depuis que le classement voit toutes les correspondances — un
     * millier sur un préfixe de deux lettres ([D161][decisions]) —, c'était l'essentiel
     * du temps d'une recherche. L'ordre ne change pas : la fonction est pure.
     *
     * [decisions]: docs/11-decisions.md
     */
    fun sort(foods: List<Food>, query: String): List<Food> {
        val normalised = SearchText.normalise(query)
        return foods
            .map { food -> food to score(food, normalised) }
            .sortedWith(compareByDescending<Pair<Food, Double>> { it.second }.thenBy { it.first.name })
            .map { it.first }
    }

    /**
     * Visible pour être testée : c'est elle qui porte la règle, pas le tri.
     *
     * **Le meilleur des noms de la fiche** : son libellé et ses noms courants, pesés par
     * la même fonction ([D164][decisions]). « riz », nom courant du riz cuit, vaut à
     * cette fiche une égalité de nom mesurée sur trois lettres, quand le riz cru n'a
     * que son libellé. Un nom courant n'est pas un bonus ajouté à côté : c'est un nom.
     *
     * **Il ne compte que s'il nomme la requête** — égalité, préfixe, mot entier ou
     * lettres en tête. Le libellé, lui, compte toujours. Sans cette condition, un nom
     * courant de trois lettres pèserait plus que tout libellé au palier le plus bas, et
     * gagnerait sur une requête qui le précise : « café sucré » rendait le café « sans
     * sucres ajoutés », parce que l'index trouve « sucres » dans son libellé et que
     * « café » est court. Une requête qui ne nomme aucun nom courant se classe
     * exactement comme avant que la table existe.
     *
     * [decisions]: docs/11-decisions.md
     */
    fun score(food: Food, normalisedQuery: String): Double {
        val closest = food.commonNames
            .filter { name -> proximity(name, normalisedQuery) > SOMEWHERE }
            .plus(SearchText.normalise(food.name))
            .maxOf { name -> proximity(name, normalisedQuery) * brevity(name) }
        return closest * food.source.weight * familiarity(food)
    }

    /**
     * Le même score, ramené dans `[0, 1[` — la seule échelle sur laquelle un seuil
     * veut dire quelque chose.
     *
     * **Le tri ne bouge pas d'un rang**, et c'est ce qui rend la consolidation
     * gratuite : `s / (s + k)` est strictement croissante, donc elle conserve l'ordre
     * de [score] exactement, égalités comprises. Un test le tient, parce que c'est la
     * propriété dont dépend un écran déjà livré.
     *
     * [SATURATION] place le seuil haut de [docs/04][sources] — 0,75 — à un score brut
     * de 1,8 : au-dessus d'une correspondance par préfixe, qui vaut de 1,1 à 1,4 sur
     * un libellé de l'ANSES, et en dessous d'une égalité de nom, qui en vaut 2,7 à
     * 3,5. Autrement dit : un nom exact suffit, un préfixe ne suffit pas seul.
     *
     * Les poids de provenance et d'usage entrent dans ce calcul, comme
     * [docs/04][sources] le veut : un aliment déjà mangé franchit le seuil sur un
     * préfixe là où un inconnu ne le franchit pas. C'est le sens du ×1,5 — une
     * ressemblance moyenne sur un aliment familier vaut mieux qu'une ressemblance
     * moyenne sur un inconnu.
     *
     * [sources]: docs/04-sources-de-donnees.md
     */
    fun confidence(food: Food, normalisedQuery: String): Double {
        val raw = score(food, normalisedQuery)
        return raw / (raw + SATURATION)
    }

    /**
     * À quel point le nom *est* la requête, plutôt que de la contenir.
     *
     * Des paliers et non un score continu : « pomme » doit passer devant « pomme
     * de terre », et un écart continu laisserait la longueur du nom rattraper la
     * différence sur un libellé CIQUAL à rallonge.
     *
     * **Un préfixe s'arrête à la fin d'un mot** ([D163][decisions]). « lait » commence
     * « Laitue, crue », qui passait devant tous les laits — et l'IA, qui écrit « lait »,
     * y tombait. Un nom qui ne commence que par les **lettres** de la requête n'est pas
     * celui qu'on cherche une fois le mot fini ; il passe derrière celui où le mot est
     * entier. Pendant qu'on écrit, en revanche, tous les candidats sont dans ce cas, et
     * ce palier les départage encore de ceux où les lettres n'arrivent que plus loin.
     *
     * [decisions]: docs/11-decisions.md
     */
    private fun proximity(name: String, query: String): Double = when {
        name == query -> EXACT
        name.startsWith("$query ") -> PREFIX
        name.split(' ').any { it == query } -> WHOLE_WORD
        name.startsWith(query) -> PARTIAL
        else -> SOMEWHERE
    }

    /**
     * Le nom court gagne.
     *
     * Un libellé CIQUAL décrit sa préparation — « Pomme de terre, chair et peau,
     * bouillie/cuite à l'eau, sans sel ajouté » — et l'aliment générique porte
     * **le plus souvent** le nom le plus court de sa famille.
     *
     * **Pas toujours** ([D163][decisions]) : la forme qu'on mange se décrit souvent plus
     * longuement que ses dérivés. « Riz blanc, cru » est plus court que « Riz blanc,
     * cuit, sans sel ajouté », « Café, moulu » que « Café, non instantané, sans sucres
     * ajoutés, prêt à boire », « Huile d'avocat » que « Avocat, chair sans peau, sans
     * noyau, cru ». Rien dans un libellé ne dit laquelle est la forme qu'on mange : ce
     * sont les noms courants qui le disent ([D164][decisions]), et la brièveté se mesure
     * alors sur le nom courant — « riz », trois lettres.
     *
     * [decisions]: docs/11-decisions.md
     */
    private fun brevity(name: String): Double = REFERENCE_LENGTH / (REFERENCE_LENGTH + name.length)

    /**
     * Ce que l'utilisateur a déjà mangé remonte.
     *
     * Pas proportionnel au nombre d'usages : un aliment consommé cent fois ne doit
     * pas enterrer un homonyme plus pertinent. Un seul palier suffit à faire remonter
     * le riz qu'on achète au-dessus des quatorze riz de la table.
     */
    private fun familiarity(food: Food): Double = if (food.useCount > 0) CONSUMED else 1.0

    /**
     * Le poids d'une provenance, tel que [docs/04][sources] le fixe.
     *
     * Le produit de marque est délibérément dévalorisé : « riz » doit tomber sur le
     * riz de la table, pas sur un paquet scanné il y a trois mois.
     *
     * [sources]: docs/04-sources-de-donnees.md
     */
    private val FoodSource.weight: Double
        get() = when (this) {
            FoodSource.CUSTOM -> 1.3
            FoodSource.CIQUAL -> 1.0
            FoodSource.OFF -> 0.8
        }

    private const val EXACT = 4.0
    private const val PREFIX = 2.0
    private const val WHOLE_WORD = 1.5

    /** Les lettres de la requête en tête, sans le mot entier : « Laitue » pour « lait ». */
    private const val PARTIAL = 1.25
    private const val SOMEWHERE = 1.0

    private const val CONSUMED = 1.5

    /** Longueur au-delà de laquelle un nom cesse d'être court. En caractères. */
    private const val REFERENCE_LENGTH = 30.0

    /**
     * Le score brut auquel la confiance vaut 0,5, et donc ce qui règle les seuils.
     *
     * Choisi pour que 0,75 tombe à 1,8 — voir [confidence]. Le déplacer déplace les
     * deux seuils ensemble, ce qui est la bonne façon de recalibrer le jour où de
     * vraies reconnaissances diront où ils devraient être.
     */
    private const val SATURATION = 0.6
}
