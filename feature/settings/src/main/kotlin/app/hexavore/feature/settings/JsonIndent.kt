package app.hexavore.feature.settings

/**
 * Du JSON mis en page, **même incomplet**.
 *
 * ### Pourquoi pas un analyseur
 *
 * `Json.parseToJsonElement` rendrait une mise en page impeccable et **échouerait
 * exactement sur les corps qui intéressent** : le journal borne ce qu'il retient, donc
 * les plus longs — ceux d'une boucle d'outillage, ceux d'une réponse inattendue —
 * arrivent ici avec une accolade en moins, et désormais avec un trou nommé au milieu.
 * Un analyseur les refuserait en bloc et laisserait l'écran afficher une seule ligne
 * illisible, c'est-à-dire précisément l'état qu'on cherchait à corriger.
 *
 * Celui-ci ne comprend rien à ce qu'il lit. Il déplace des retours à la ligne selon la
 * ponctuation, et une accolade manquante ne lui fait ni chaud ni froid. Ce qui n'est pas
 * du JSON du tout — une page d'erreur, un message de passerelle — le traverse intact,
 * ce qui est exactement ce qu'on veut d'un écran de diagnostic.
 *
 * La reconnaissance des chaînes est confiée à [StringTracker] : ce sont deux questions
 * distinctes — *suis-je dans une chaîne ?* et *où vont les retours à la ligne ?* — et
 * les mêler dans une seule boucle rendait la seconde illisible.
 */
internal fun String.indentedJson(): String {
    val out = StringBuilder(length + length / GROWTH_DIVISOR)
    val strings = StringTracker()
    var depth = 0

    for (char in this) {
        if (strings.isLiteral(char)) {
            out.append(char)
            continue
        }
        when (char) {
            '{', '[' -> {
                depth++
                out.append(char).newLine(depth)
            }

            '}', ']' -> {
                depth = (depth - 1).coerceAtLeast(0)
                out.newLine(depth).append(char)
            }

            ',' -> out.append(char).newLine(depth)

            ':' -> out.append(": ")

            // Les espaces deja presents feraient double emploi avec ceux qu'on pose --
            // mais **seulement a l'interieur d'une structure**. Hors de toute accolade,
            // ce qu'on lit n'est pas du JSON : c'est une page d'erreur ou un message de
            // passerelle, et lui retirer ses espaces collerait les mots.
            else -> if (!char.isWhitespace() || depth == 0) out.append(char)
        }
    }
    return out.toString()
}

/**
 * Où l'on en est d'une chaîne de caractères, en parcourant du JSON.
 *
 * **Deux règles, et chacune corrige un défaut qu'on verrait tout de suite.** Un `{`
 * dans un libellé d'aliment ou dans un prompt n'ouvre pas un objet — et le prompt du
 * projet parle de JSON, donc il en contient. Un `\"` échappé ne ferme pas la chaîne :
 * sans cela, tout ce qui suit une citation serait traité comme de la structure.
 */
private class StringTracker {
    private var inString = false
    private var escaped = false

    /**
     * Ce caractère est-il du **texte**, par opposition à la ponctuation de structure ?
     *
     * Consomme le caractère : appeler deux fois avec le même fausse l'état.
     */
    fun isLiteral(char: Char): Boolean = when {
        escaped -> {
            escaped = false
            true
        }

        inString -> {
            when (char) {
                '\\' -> escaped = true
                '"' -> inString = false
            }
            true
        }

        char == '"' -> {
            inString = true
            true
        }

        else -> false
    }
}

private fun StringBuilder.newLine(depth: Int): StringBuilder {
    append('\n')
    repeat(depth) { append(INDENT) }
    return this
}

private const val INDENT = "  "

/**
 * De combien on surdimensionne le tampon.
 *
 * L'indentation ajoute grosso modo un quart de la longueur ; réserver d'avance évite
 * une poignée de recopies sur un corps qui se compte en dizaines de milliers de
 * caractères.
 */
private const val GROWTH_DIVISOR = 4
