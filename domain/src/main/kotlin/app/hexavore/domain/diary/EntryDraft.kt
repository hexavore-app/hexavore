package app.hexavore.domain.diary

import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.identity.IdGenerator
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.NutrientValues
import app.hexavore.domain.profile.UnitSystem
import java.time.LocalDate

/**
 * Identifiant d'une ligne de brouillon.
 *
 * Distinct de [EntryId] à dessein : une ligne existe à l'écran avant d'exister dans
 * le journal, et pendant ce temps la liste doit quand même pouvoir la désigner —
 * pour la modifier, pour la supprimer, pour ne pas la recomposer entièrement à
 * chaque frappe. Réutiliser [EntryId] obligerait à en inventer un pour du contenu
 * qui n'est pas enregistré, donc à ne plus pouvoir distinguer les deux états.
 */
@JvmInline
value class DraftLineId(val value: String)

/**
 * Une ligne de l'écran de validation.
 *
 * Ses champs sont **facultatifs**, et ce n'est pas un relâchement : une ligne en
 * cours de saisie est incomplète par nature, et représenter « pas encore renseigné »
 * par un zéro serait la même erreur que confondre `null` avec zéro sur une valeur
 * nutritionnelle. [complete] dit quand la ligne est enregistrable.
 *
 * [values] porte les six valeurs **indépendamment les unes des autres**, énergie
 * comprise. Une seule d'entre elles peut manquer sans emporter les autres, et c'est
 * ce qui permet à un aliment CIQUAL sans énergie déterminée — la feta, les câpres —
 * d'arriver ici avec ses protéines et ses lipides, l'énergie seule restant à
 * compléter.
 *
 * Aucune source ici. Elle appartient au plat, et c'est ce qui permet à cet écran
 * d'accepter indifféremment une ligne tapée à la main, un résultat de recherche, un
 * produit scanné ou une proposition de modèle.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
data class DraftLine(
    val id: DraftLineId,
    /**
     * L'entrée de journal dont cette ligne provient, si elle en provient d'une.
     *
     * `null` pour une ligne ajoutée à l'écran. La conserver permet à une
     * modification de réécrire la même ligne plutôt que d'en créer une seconde,
     * et donc de préserver sa date de création.
     */
    val entryId: EntryId? = null,
    /** La fiche d'où vient cette ligne. Provenance seulement — voir [FoodEntry.foodId]. */
    val foodId: FoodId? = null,
    /**
     * La fiche elle-même, quand elle vient d'être choisie et n'est peut-être pas
     * encore au catalogue.
     *
     * `null` sur une ligne tapée à la main, et `null` aussi sur une ligne rouverte :
     * dans ce second cas [foodId] suffit, puisque la fiche est forcément déjà au
     * catalogue — elle y est entrée quand le plat a été enregistré. La porter ici
     * évite d'aller la relire pour l'écrire.
     */
    val food: Food? = null,
    val name: String = "",
    val quantity: Double? = null,
    val unit: QuantityUnit = QuantityUnit.Gram,
    val values: NutrientValues = NutrientValues(),
    /**
     * Les portions que la fiche d'origine propose, en plus des grammes et des
     * millilitres.
     *
     * Vide pour une ligne tapée à la main : il n'y a pas de fiche derrière, donc
     * rien qui puisse dire ce que pèse une tranche.
     */
    val servings: List<QuantityUnit.Serving> = emptyList(),
    /**
     * Les valeurs pour 100 g dont cette ligne dérive.
     *
     * C'est elle qui permet à la quantité de recalculer, et **elle ne vient pas de
     * la fiche courante** : elle est capturée à la naissance de la ligne, et
     * reconstruite depuis les valeurs figées quand on rouvre un plat. Un fabricant
     * qui reformule son produit ne doit pas réécrire un journal vieux de six mois
     * ([D05][decisions]) — corriger une quantité ne doit pas être l'occasion d'aller
     * relire une fiche qui a changé, ni d'en exiger une qui a pu être supprimée.
     *
     * `null` quand il n'y a rien à recalculer : une ligne vierge, ou une ligne dont
     * la quantité enregistrée était nulle et ne permet aucune règle de trois.
     *
     * [decisions]: docs/11-decisions.md
     */
    val reference: NutrientValues? = null,
    /**
     * Les valeurs que l'utilisateur a écrites lui-même.
     *
     * Elles ne sont **plus jamais recalculées** pour cette ligne, ce que
     * [docs/02][parcours] demande depuis la conception. Corriger les calories d'une
     * pomme parce que la sienne est petite, puis voir le chiffre revenir en changeant
     * d'unité, serait incompréhensible — et la correction serait perdue sans que rien
     * ne prévienne.
     *
     * [parcours]: docs/02-parcours-et-ecrans.md
     */
    val edited: Set<Macro> = emptySet(),
    /**
     * Celles des valeurs de cette ligne qui viennent d'une **fiche complétée par un
     * modèle** plutôt que d'une mesure.
     *
     * Héritée de [Food.estimated] à la naissance de la ligne, et recalculée avec le
     * reste : une valeur estimée pour 100 g reste estimée pour 180 g. C'est ce qui
     * permet à l'écran de valider de dire, champ par champ, ce qui a été mesuré et
     * ce qui a été deviné.
     *
     * **Corriger une valeur à la main efface sa marque** : elle n'est plus l'avis
     * d'un modèle mais celui de l'utilisateur, et continuer à la présenter comme
     * incertaine serait faux. Les deux ensembles sont donc exclusifs par
     * construction — voir [corrected].
     *
     * Distincte de `Suggestion.estimatedMacros`, qui dit que **la ligne entière** a
     * été devinée par un modèle qu'on venait d'interroger ([D83][decisions]).
     * Celle-ci dit qu'une fiche du catalogue avait un trou, et lequel. Les deux
     * peuvent coexister, et elles se trompent séparément.
     *
     * [decisions]: docs/11-decisions.md
     */
    val estimated: Set<Macro> = emptySet(),
    /**
     * Ce qu'un modèle a proposé pour cette ligne, quand c'est un modèle qui l'a
     * proposée.
     *
     * `null` sur toutes les autres — tapée à la main, cherchée, scannée, rejouée
     * depuis un favori — et c'est exactement ce qui la rend lisible : une ligne
     * **sans** suggestion ne porte aucune marque, parce qu'elle n'est pas une
     * supposition.
     *
     * Elle ne survit pas à l'enregistrement, et ce n'est pas un oubli. Ce qui reste
     * du passage d'un modèle est la **source du plat** ([EntrySource.TEXT_AI],
     * [EntrySource.PHOTO_AI]) ; la confiance et les alternatives ne décrivent qu'un
     * moment — celui où l'écran demandait une relecture — et le plat validé n'est
     * plus une proposition.
     */
    val suggestion: Suggestion? = null,
) {
    /** La quantité convertie en grammes, base de tout calcul. */
    val grams: Double? get() = quantity?.times(unit.gramsPerUnit)

    /**
     * `true` quand la ligne peut être enregistrée.
     *
     * Un nom, une quantité strictement positive, une énergie. Les cinq autres
     * valeurs restent facultatives : un produit mal renseigné doit pouvoir entrer
     * dans le journal avec ses trous visibles, plutôt que de ne pas y entrer.
     */
    val complete: Boolean
        get() = name.isNotBlank() && (quantity ?: 0.0) > 0.0 && values.kcal != null

    /** `true` quand rien n'a été saisi — le cas d'une ligne qu'on vient d'ajouter. */
    val blank: Boolean
        get() = name.isBlank() && quantity == null && values.empty

    /**
     * Les unités proposées : les deux du système choisi, les portions de la fiche,
     * **et celle que la ligne porte déjà**.
     *
     * Ce dernier terme n'est pas une précaution, c'est une correction. Rouvrir un plat
     * reconstruit son unité depuis ce qui a été écrit — « 1 bol » redevient une
     * portion nommée — mais sans relire la fiche, donc sans ses portions. Le
     * sélecteur ne proposait plus que les grammes et les millilitres, et ne pouvait
     * pas montrer comme choisie une unité absente de sa propre liste : la quantité
     * restait juste, l'écran mentait.
     *
     * La comparaison porte sur le **code** et non sur l'unité entière : une portion
     * qui pèserait un gramme de plus que celle de la fiche apparaîtrait deux fois
     * sous le même nom.
     *
     * **C'est aussi ce qui rend le réglage d'unités sans danger.** Basculer en
     * impérial ne propose plus les grammes ; un plat noté en grammes garde pourtant
     * les siens, parce que la ligne apporte son unité avec elle.
     */
    fun units(system: UnitSystem): List<QuantityUnit> = (QuantityUnit.universal(system) + servings)
        .let { proposees -> if (proposees.any { it.code == unit.code }) proposees else proposees + unit }

    /**
     * La même ligne, pour une autre quantité.
     *
     * **Les valeurs suivent, sauf celles qu'on a corrigées à la main.** C'est le
     * point où le recalcul demandé par [docs/02][parcours] a enfin une référence, et
     * c'est aussi le point où il pouvait le plus facilement effacer le travail de
     * l'utilisateur.
     *
     * Sans [reference], rien ne bouge : une ligne dont on ne connaît pas les valeurs
     * pour 100 g n'a aucune règle de trois à appliquer, et en inventer une
     * réécrirait des chiffres que personne n'a donnés ([D42][decisions]).
     *
     * [parcours]: docs/02-parcours-et-ecrans.md
     * [decisions]: docs/11-decisions.md
     */
    fun measured(quantity: Double?, unit: QuantityUnit = this.unit): DraftLine {
        val moved = copy(quantity = quantity, unit = unit)
        val scaled = moved.grams?.let { grams -> reference?.per(grams) }
        return when (scaled) {
            null -> moved
            else -> moved.copy(values = scaled.keeping(edited, values))
        }
    }

    /** Les valeurs recalculées, celles corrigées à la main laissées telles quelles. */
    private fun NutrientValues.keeping(edited: Set<Macro>, corrections: NutrientValues): NutrientValues =
        edited.fold(this) { values, macro -> values.with(macro, corrections[macro]) }

    /**
     * La même ligne, une valeur corrigée à la main.
     *
     * La marque est posée même quand la valeur est effacée : vider un champ est une
     * affirmation — « je ne sais pas » — et la quantité n'a pas à la contredire au
     * gramme suivant.
     */
    fun corrected(macro: Macro, value: Double?): DraftLine =
        // La marque d'estimation tombe : cette valeur n'est plus l'avis d'un modele
        // mais celui de l'utilisateur, et continuer a la presenter comme incertaine
        // serait faux.
        copy(values = values.with(macro, value), edited = edited + macro, estimated = estimated - macro)

    companion object {
        /** Une ligne vierge, telle que la produit « Ajouter une ligne ». */
        fun blank(id: DraftLineId): DraftLine = DraftLine(id = id)

        /**
         * Une ligne préremplie depuis une fiche d'aliment.
         *
         * **C'est ici que le recalcul à la quantité a enfin une référence**, ce que
         * [D42][decisions] attendait : les valeurs sont celles de la fiche pour
         * 100 g, ramenées à la quantité choisie. Une valeur inconnue le reste — il
         * n'y a pas de multiplication d'un inconnu.
         *
         * La quantité proposée est celle de la fiche : sa portion par défaut si elle
         * en a une, sinon son poids de service, sinon 100 g. Personne ne pèse une
         * pomme.
         *
         * [decisions]: docs/11-decisions.md
         */
        fun of(id: DraftLineId, food: Food): DraftLine {
            val servings = food.servings.map { QuantityUnit.Serving(it.label, it.grams) }
            val unit = food.defaultServing?.let { QuantityUnit.Serving(it.label, it.grams) }
            val quantity = if (unit != null) 1.0 else food.defaultServingG ?: NutrientValues.REFERENCE_GRAMS

            return DraftLine(
                id = id,
                foodId = food.id,
                food = food,
                // Le titre court quand la fiche en a un : c'est ce nom-la que le
                // journal figera, et l'accueil est l'ecran ou un libelle a rallonge
                // se paie tous les jours. Le libelle d'origine reste lisible sous le
                // champ, porte par [food].
                name = food.displayName,
                quantity = quantity,
                unit = unit ?: QuantityUnit.Gram,
                values = food.per100g.per(quantity * (unit?.gramsPerUnit ?: 1.0)),
                servings = servings,
                reference = food.per100g,
                // Une valeur estimee pour 100 g reste estimee pour 180 g : la regle
                // de trois ne transforme pas une supposition en mesure.
                estimated = food.estimated,
            )
        }

        /**
         * Une ligne rouverte, avec la référence reconstruite depuis ses valeurs figées.
         *
         * La règle de trois est exacte et **ne relit aucune fiche** : les valeurs
         * affichées à l'ouverture restent celles enregistrées ce jour-là, puisque la
         * quantité n'a pas bougé. C'est seulement en la changeant qu'elles suivent —
         * et elles suivent la ligne, pas la fiche, qui a pu être corrigée ou
         * supprimée depuis ([D05][decisions]).
         *
         * [decisions]: docs/11-decisions.md
         */
        fun referenceOf(values: NutrientValues, grams: Double): NutrientValues? {
            // Une quantite nulle ne permet aucune regle de trois. La ligne se
            // modifie quand meme, ses valeurs ne suivront simplement pas.
            if (grams <= 0.0) return null
            val hundredGrams = NutrientValues.REFERENCE_GRAMS
            return values.per(hundredGrams * hundredGrams / grams)
        }
    }
}

/**
 * Ce que l'écran de validation manipule : *n* lignes et le plat qu'elles formeront.
 *
 * **C'est le point de convergence des quatre modes de saisie**, et la raison d'être
 * de ce type. Une recherche produit un brouillon d'une ligne, un scan aussi, une
 * photo en produit cinq ; l'écran ne fait pas la différence, parce qu'il n'a jamais
 * accès à autre chose que cet objet. Écrire cet écran « pour la saisie manuelle »
 * obligerait à le généraliser trois fois — c'est le piège central du projet, et il
 * est signalé dans [docs/12][plan] depuis la conception.
 *
 * [source] est portée par le brouillon et non par ses lignes : on ne photographie
 * pas une assiette aliment par aliment. Elle est **ignorée** lors d'une
 * modification, où l'origine du plat existant fait foi ([D32][decisions]).
 *
 * [plan]: docs/12-plan-de-developpement.md
 * [decisions]: docs/11-decisions.md
 */
data class EntryDraft(
    /** `null` pour une nouvelle saisie ; renseigné quand on modifie un plat existant. */
    val dishId: DishId? = null,
    val date: LocalDate,
    val source: EntrySource,
    val lines: List<DraftLine>,
    /**
     * Le favori que ce brouillon rejoue, ou auquel le plat est rattaché.
     *
     * Posé en ouvrant un favori, et en rouvrant un plat qui en vient. Il **tombe dès
     * qu'une ligne est touchée** — ajoutée, modifiée, supprimée : le brouillon cesse
     * alors d'être celui que le favori décrit ([D62][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    val favoriteId: FavoriteDishId? = null,
    /**
     * Le titre écrit à la main, ou `null` tant que personne n'y a touché.
     *
     * **`null` et non le libellé du moment**, alors même que l'écran l'affiche déjà
     * dans son champ : ce que le champ montre est une proposition, et l'enregistrer
     * comme une saisie figerait des mots que l'utilisateur n'a pas écrits. C'est la
     * même distinction qu'entre une valeur estimée et une valeur corrigée à la main,
     * appliquée à un nom.
     */
    val title: String? = null,
    /**
     * Le moment auquel ce plat se rattachera.
     *
     * Posé par [CreateDraft][app.hexavore.domain.usecase.CreateDraft] depuis l'heure
     * qu'il est, ou relu du plat qu'on rouvre. Les quatre pastilles de l'écran le
     * changent — c'est le seul moyen de rattraper un repas noté le lendemain, dont
     * l'heure de saisie ne dit rien du repas.
     */
    val moment: MealMoment,
) {
    /** Le même brouillon, détaché de son favori. Tout geste sur les lignes y passe. */
    fun unlinked(): EntryDraft = if (favoriteId == null) this else copy(favoriteId = null)

    /** `true` quand ce brouillon modifie un plat déjà enregistré. */
    val editing: Boolean get() = dishId != null

    /**
     * `true` quand ce brouillon, enregistré, **supprimerait** le plat qu'il modifie.
     *
     * Retirer les lignes une à une jusqu'à la dernière est une façon parfaitement
     * naturelle de dire « ce plat n'a pas eu lieu », et c'est déjà ce que fait le
     * balayage à l'accueil : `DeleteEntry` supprime le plat vidé de sa dernière ligne.
     * Le même geste sur l'écran de validation butait au contraire sur un refus — « il
     * faut au moins une ligne » — qui demandait de sortir et de recommencer autrement.
     *
     * Une saisie **neuve** vidée ne supprime rien : il n'y a rien à supprimer, et
     * l'enregistrer n'aurait aucun sens. C'est [editing] qui fait la différence.
     */
    val emptying: Boolean get() = editing && lines.isEmpty()

    /**
     * `true` quand l'enregistrement est possible.
     *
     * Toutes les lignes, et pas seulement une : une ligne à moitié remplie qu'on
     * enregistrerait silencieusement serait une donnée inventée.
     *
     * Un brouillon sans aucune ligne est enregistrable **s'il modifie un plat** : ce
     * qu'il enregistre est alors la disparition de ce plat ([emptying]).
     */
    val saveable: Boolean get() = if (lines.isEmpty()) editing else lines.all { it.complete }

    /** L'énergie de la saisie, sur les seules lignes qui en portent une. */
    val kcal: Double get() = lines.sumOf { it.values.kcal ?: 0.0 }

    /**
     * Les fiches à verser au catalogue à l'enregistrement.
     *
     * Vide pour un plat tapé à la main, et vide aussi pour un plat rouvert : ses
     * fiches y sont déjà entrées le jour où il a été enregistré.
     */
    val foods: List<Food> get() = lines.mapNotNull { it.food }.distinctBy { it.id }
}

/**
 * Les lignes du brouillon, telles qu'elles entreront dans le journal.
 *
 * Les identifiants manquants sont tirés de [ids] plutôt que du hasard ambiant :
 * c'est ce qui rend `LogDish` vérifiable autrement qu'en relisant la base.
 *
 * @param placed ce que `FoodUsage.remember` vient d'écrire : la correspondance entre
 *   l'identifiant qu'une ligne portait et celui sous lequel la fiche est rangée. Vide
 *   pour un plat rouvert, dont les fiches sont déjà au catalogue.
 *
 * @throws IllegalStateException si une ligne est incomplète. L'appelant vérifie
 *   [EntryDraft.saveable] avant : une ligne à moitié saisie n'a pas de conversion
 *   raisonnable, et en inventer une écrirait un chiffre que personne n'a donné.
 */
fun EntryDraft.toEntries(dishId: DishId, ids: IdGenerator, placed: Map<FoodId, FoodId> = emptyMap()): List<FoodEntry> =
    lines.map { line ->
        FoodEntry(
            id = line.entryId ?: EntryId(ids.next()),
            dishId = dishId,
            // La fiche **telle qu'elle est rangee**, et non telle que la ligne l'a connue :
            // un resultat de recherche porte un identifiant provisoire, et une entree qui
            // le citerait designerait une fiche absente. La base refuse, et l'ecran
            // annonce « l'ecriture n'a pas abouti » sans pouvoir dire pourquoi.
            foodId = line.foodId?.let { placed[it] ?: it },
            displayName = line.name.trim(),
            quantity = checkNotNull(line.quantity) { "Ligne sans quantite : ${line.name}" },
            unit = line.unit.code,
            grams = checkNotNull(line.grams) { "Ligne sans quantite : ${line.name}" },
            // Les cinq valeurs facultatives traversent telles quelles ; seule l'energie
            // est exigee, et son absence est une erreur de l'appelant, pas un zero.
            macros = checkNotNull(line.values.toMacros()) { "Ligne sans energie : ${line.name}" },
        )
    }
