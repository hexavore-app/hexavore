package app.hexavore.tooling.ciqual

import app.hexavore.domain.food.FoodCategory
import app.hexavore.domain.language.ContentLanguage

/** Une teneur dont l'écriture n'est reconnue par aucune règle du parseur. */
internal data class UnrecognisedValue(val foodCode: String, val constCode: String, val raw: String) {
    override fun toString(): String = "aliment $foodCode, constituant $constCode : [$raw]"
}

/** Ce que l'archive contient, une fois lue et interprétée. */
internal data class CiqualTable(val foods: List<CiqualFood>, val unrecognised: List<UnrecognisedValue>)

/**
 * L'assemblage : trois fichiers croisés pour produire une fiche par aliment.
 *
 * L'ordre de lecture n'est pas indifférent. Les constituants sont vérifiés
 * **avant** tout le reste : découvrir une renumérotation après avoir traversé 69 Mo
 * serait une minute perdue à chaque essai, et surtout une occasion de ne pas voir
 * l'erreur parce qu'elle arriverait au milieu d'un flot de messages.
 */
internal class CiqualReader(private val archive: CiqualArchive) {
    fun read(): CiqualTable {
        verifyConstituents()
        val groups = ContentLanguage.entries.associateWith { readGroups(it) }
        // La verification porte sur les codes et leurs intitules francais : c'est dans
        // cette langue que CiqualCategories declare ses 45 arbitrages, et une table de
        // correspondance ne se controle que contre la langue dans laquelle elle a ete
        // ecrite.
        verifyCategories(groups.getValue(ContentLanguage.FRENCH))
        val names = readFoods(groups)
        val (nutrients, unrecognised) = readCompositions(names.keys)

        val foods =
            names.map { (code, identity) ->
                CiqualFood(
                    code = code,
                    labels = identity.labels,
                    category = identity.category,
                    nutrients = nutrients[code].orEmpty(),
                )
            }
        return CiqualTable(foods.sortedBy { it.code }, unrecognised)
    }

    /**
     * Le contrôle qui protège d'une erreur qu'aucun test ne verrait.
     *
     * Si l'ANSES renumérotait ses constituants, l'import continuerait de tourner et
     * remplirait la colonne des lipides avec autre chose. La base se générerait,
     * l'application se lancerait, et les chiffres seraient faux.
     */
    private fun verifyConstituents() {
        val labels = mutableMapOf<String, String>()
        archive.constituents { record ->
            val code = record["const_code"].orEmpty()
            if (Nutrient.byCode(code) != null) labels[code] = record["const_nom_fr"].orEmpty()
        }

        val drifted =
            Nutrient.entries.filter { labels[it.constCode] != it.expectedLabel }.map {
                "  ${it.name} (code ${it.constCode}) : attendu [${it.expectedLabel}], lu [${labels[it.constCode]}]"
            }

        if (drifted.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Les codes de constituants CIQUAL ne portent plus les memes intitules :")
                    drifted.forEach { appendLine(it) }
                    appendLine()
                    append("Verifier const_*.xml et corriger Nutrient.kt avant de regenerer la base.")
                },
            )
        }
    }

    /**
     * Le rayon d'un aliment.
     *
     * Le sous-groupe plutôt que le groupe : « soupes » situe mieux que « entrées et
     * plats composés », et c'est ce qui départage deux résultats de recherche
     * homonymes. Le groupe sert de repli pour les aliments qui n'ont pas de
     * sous-groupe renseigné.
     */
    private fun readGroups(language: ContentLanguage): Map<String, String> {
        val field = language.field
        val names = mutableMapOf<String, String>()
        archive.groups { record ->
            record["alim_grp_code"]?.let { names.putIfAbsent(it, record["alim_grp_nom_$field"].orEmpty()) }
            record["alim_ssgrp_code"]?.let { names.putIfAbsent(it, record["alim_ssgrp_nom_$field"].orEmpty()) }
        }
        return names.filterValues { it.isNotBlank() && it != "-" }
    }

    /**
     * Le pendant de [verifyConstituents], pour la table des rayons.
     *
     * Deux dérives sont possibles et aucune ne se voit à l'exécution. L'ANSES peut
     * **renuméroter** — et « 0405 » désignerait alors autre chose que les poissons,
     * qui se retrouveraient sous un rayon faux. Elle peut aussi **ajouter** un
     * sous-groupe, qui n'aurait alors aucun rayon sans que personne l'ait décidé :
     * un silence qui ressemble en tout point à un arbitrage.
     *
     * Les deux arrêtent l'import. C'est le même raisonnement qu'en [D49][decisions]
     * pour une écriture de teneur inconnue : un repli silencieux se découvre des mois
     * plus tard, et sur l'appareil de quelqu'un.
     *
     * [decisions]: docs/11-decisions.md
     */
    private fun verifyCategories(groups: Map<String, String>) {
        val drifted = CiqualCategories.drifted(groups)
        val unknown = groups.keys.filterNot { CiqualCategories.knows(it) }.sorted().map {
            "  $it : [${groups[it]}] -- sous-groupe inconnu de la table des rayons"
        }
        if (drifted.isEmpty() && unknown.isEmpty()) return

        error(
            buildString {
                appendLine("La nomenclature CIQUAL ne correspond plus a la table des rayons :")
                (drifted + unknown).forEach(::appendLine)
                appendLine()
                appendLine("Un rayon faux se voit chez l'utilisateur, pas ici : un aliment sort")
                appendLine("sous une pastille a laquelle il n'appartient pas, ou sous aucune.")
                appendLine()
                append("Arbitrer chaque ligne dans CiqualCategories, puis incrementer sa VERSION.")
            },
        )
    }

    /**
     * Les identités, une passe par langue.
     *
     * **Une fiche n'entre que si elle est nommée dans toutes les langues.** Un libellé
     * manquant dans l'une d'elles rendrait l'aliment introuvable pour qui affiche cette
     * langue, et introuvable en silence : la recherche ne rendrait rien, et rien ne
     * dirait pourquoi. L'ANSES nomme ses 3 484 lignes dans les deux, donc le cas
     * n'arrive pas — et c'est justement pour qu'il ne s'installe pas sans bruit le jour
     * où elle en oublierait une que la condition est écrite.
     */
    private fun readFoods(groups: Map<ContentLanguage, Map<String, String>>): Map<String, FoodIdentity> {
        val labels = ContentLanguage.entries.associateWith { language ->
            readLabels(language, groups.getValue(language))
        }
        val categories = readCategories()
        val named = labels.values.map { it.keys }.reduce { shared, next -> shared intersect next }

        return categories
            .filterKeys { it in named }
            .mapValues { (code, category) ->
                FoodIdentity(
                    labels = labels.mapValues { (_, byCode) -> byCode.getValue(code) },
                    category = category,
                )
            }
    }

    private fun readLabels(language: ContentLanguage, groups: Map<String, String>): Map<String, CiqualLabel> {
        val field = language.field
        val labels = linkedMapOf<String, CiqualLabel>()
        archive.foods { record ->
            val code = record["alim_code"].orEmpty()
            val name = record["alim_nom_$field"].orEmpty()
            if (code.isBlank() || name.isBlank()) return@foods
            labels[code] =
                CiqualLabel(
                    name = name,
                    groupName = groups[record["alim_ssgrp_code"]] ?: groups[record["alim_grp_code"]],
                )
        }
        return labels
    }

    /** Le rayon de chaque aliment : il ne dépend d'aucune langue, seulement des codes. */
    private fun readCategories(): Map<String, FoodCategory?> {
        val categories = linkedMapOf<String, FoodCategory?>()
        archive.foods { record ->
            val code = record["alim_code"].orEmpty()
            if (code.isBlank()) return@foods
            categories[code] = CiqualCategories.of(record["alim_ssgrp_code"], record["alim_grp_code"])
        }
        return categories
    }

    /**
     * Les teneurs, filtrées aux huit colonnes retenues.
     *
     * `CiqualValue.Unknown` **n'est pas stocké** : l'absence de clé est l'inconnu.
     * C'est ce qui rend impossible de le confondre avec zéro plus loin — il n'y a
     * rien à confondre, et aucune valeur par défaut à écrire nulle part.
     */
    private fun readCompositions(
        known: Set<String>,
    ): Pair<Map<String, Map<Nutrient, Double>>, List<UnrecognisedValue>> {
        val values = mutableMapOf<String, MutableMap<Nutrient, Double>>()
        val unrecognised = mutableListOf<UnrecognisedValue>()

        archive.compositions { record ->
            val code = record["alim_code"].orEmpty()
            val nutrient = Nutrient.byCode(record["const_code"].orEmpty())
            if (nutrient == null || code !in known) return@compositions

            when (val value = CiqualValueParser.parse(record["teneur"])) {
                is CiqualValue.Known -> values.getOrPut(code) { mutableMapOf() }[nutrient] = value.amount
                CiqualValue.Unknown -> Unit
                is CiqualValue.Unrecognised ->
                    unrecognised += UnrecognisedValue(code, nutrient.constCode, value.raw)
            }
        }
        return values to unrecognised
    }

    private data class FoodIdentity(val labels: Map<ContentLanguage, CiqualLabel>, val category: FoodCategory?)
}

/**
 * Le suffixe sous lequel l'ANSES publie cette langue.
 *
 * `alim_nom_fr` mais `alim_nom_eng` : trois lettres d'un côté, deux de l'autre, et c'est
 * l'ANSES qui en décide. Le `when` est exhaustif pour que l'ajout d'une langue au domaine
 * **ne compile pas** tant que personne n'a vérifié que la table la publie : c'est la
 * seule chose qu'on ne peut pas décider depuis ce dépôt, et la plus facile à supposer.
 */
private val ContentLanguage.field: String
    get() = when (this) {
        ContentLanguage.FRENCH -> "fr"
        ContentLanguage.ENGLISH -> "eng"
    }
