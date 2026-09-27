package app.hexavore.tooling.ciqual

import app.hexavore.domain.food.FoodCategory
import app.hexavore.domain.language.ContentLanguage

/**
 * Une fiche de l'ANSES pour les cas, nommée dans toutes les langues d'un coup.
 *
 * **Le même libellé dans les deux langues par défaut**, et ce n'est pas une paresse :
 * l'immense majorité des cas de ce module ne parlent pas de langue — ils parlent de
 * teneurs, de trous, de rayons — et leur faire écrire deux libellés aurait ajouté du bruit
 * à chacun d'eux. Les cas qui parlent de langue, eux, passent [englishName] et vérifient
 * que les deux lignes diffèrent.
 *
 * **Toutes les langues sont remplies, jamais une seule.** C'est l'invariant que
 * [CiqualReader] tient : une fiche qui manquerait dans une langue serait introuvable pour
 * qui l'affiche, et silencieusement. Un décor qui pourrait en omettre une laisserait
 * passer un écrivain qui les omet aussi.
 */
internal fun ciqualFood(
    code: String,
    name: String,
    englishName: String = name,
    groupName: String? = null,
    englishGroupName: String? = groupName,
    category: FoodCategory? = null,
    nutrients: Map<Nutrient, Double> = emptyMap(),
) = CiqualFood(
    code = code,
    labels = mapOf(
        ContentLanguage.FRENCH to CiqualLabel(name = name, groupName = groupName),
        ContentLanguage.ENGLISH to CiqualLabel(name = englishName, groupName = englishGroupName),
    ),
    category = category,
    nutrients = nutrients,
)

/** Une portion pour les cas, avec le même libellé dans les deux langues par défaut. */
internal fun ciqualServing(
    code: String,
    label: String,
    grams: Double,
    isDefault: Boolean,
    englishLabel: String = label,
) = CiqualServing(
    code = code,
    labels = mapOf(ContentLanguage.FRENCH to label, ContentLanguage.ENGLISH to englishLabel),
    grams = grams,
    isDefault = isDefault,
)
