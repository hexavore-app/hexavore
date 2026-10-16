package app.hexavore.tooling.ciqual

import app.hexavore.domain.food.SearchText
import java.io.File

/**
 * Un nom courant associé à un code CIQUAL, **déjà normalisé**.
 *
 * Normalisé à la lecture, par [SearchText] : c'est sous cette forme qu'il entre dans
 * l'index plein texte et qu'il est comparé à une saisie. Le fichier, lui, garde les
 * accents, parce qu'il se relit à la main.
 */
internal data class CiqualCommonName(val code: String, val name: String)

/**
 * La table des noms courants, écrite et **relue à la main** ([D164][decisions]).
 *
 * Le classement suppose que la fiche générique porte le nom le plus court de sa
 * famille, et sur CIQUAL c'est souvent faux : « Riz blanc, cru » est plus court que
 * « Riz blanc, cuit, sans sel ajouté », « Café, moulu » que le café qu'on boit. Ce
 * fichier dit quelle fiche est la forme qu'on mange, en lui donnant le nom sous
 * lequel on la demande. **Un nom courant est un nom** : indexé avec le libellé, pesé
 * par le même score ([D74][decisions]).
 *
 * La sévérité est celle de [ShortNamesCsv], pour la même raison : c'est ici qu'une
 * correction à la main se trompe. Chaque refus arrête l'import en nommant la ligne :
 * - un code qui n'existe pas ;
 * - un nom vide une fois normalisé ;
 * - un nom déjà donné plus haut — **un nom ne désigne qu'une fiche**, sans quoi les
 *   deux seraient à égalité sur le mot même qu'on a voulu trancher ;
 * - un nom égal au libellé d'une fiche : d'une **autre**, elles seraient à égalité ;
 *   de la **sienne**, il ne servirait à rien.
 *
 * [decisions]: docs/11-decisions.md
 */
internal object CommonNamesCsv {
    private const val HEADER = "code_ciqual,nom_courant"

    /**
     * Un fichier absent, ou réduit à ses commentaires, rend une liste vide.
     *
     * Comme pour les titres courts : une langue sans table classe ses fiches sur leur
     * seul libellé, ce qui est l'état d'avant cette table et non une erreur.
     *
     * @param labels les libellés de **la langue du fichier**, par code. Un nom courant
     *   anglais comparé à un libellé français ne voudrait rien dire.
     */
    fun read(file: File, labels: Map<String, String>): List<CiqualCommonName> {
        val lines =
            file
                .takeIf(File::exists)
                ?.readLines()
                .orEmpty()
                .map(String::trim)
                .withIndex()
                .filter { (_, line) -> line.isNotEmpty() && !line.startsWith("#") }

        val header = lines.firstOrNull() ?: return emptyList()
        check(header.value == HEADER) {
            "${file.name}:${header.index + 1} : en-tete attendu [$HEADER], lu [${header.value}]."
        }

        val reader = Reader(file, labels)
        return lines.drop(1).map { (index, line) -> reader.parse(index + 1, line) }
    }

    /**
     * La lecture d'un fichier, ligne à ligne, avec ce qu'elle a déjà vu.
     *
     * Un état et non une passe après coup : le doublon se signale **à sa ligne**, en
     * nommant celle qui portait déjà le nom, et c'est ce qui le rend corrigeable sans
     * chercher.
     */
    private class Reader(private val file: File, private val labels: Map<String, String>) {
        /**
         * Les libellés normalisés, et les codes qui portent chacun.
         *
         * Plusieurs codes et non un : deux fiches de l'ANSES peuvent ne différer que par
         * la ponctuation, et un nom égal à leur libellé commun ne les départagerait pas.
         */
        private val codesByLabel: Map<String, List<String>> =
            labels.entries.groupBy({ SearchText.normalise(it.value) }, { it.key })

        /** Les noms déjà lus, et la ligne qui les a donnés. */
        private val seen = mutableMapOf<String, Pair<Int, String>>()

        fun parse(lineNumber: Int, line: String): CiqualCommonName {
            val where = "${file.name}:$lineNumber"
            // Sur la premiere virgule seulement, comme les titres courts : un nom
            // courant peut en contenir une.
            val separator = line.indexOf(',')
            check(separator > 0) { "$where : 2 colonnes attendues, separateur introuvable." }

            val code = line.take(separator).trim()
            val raw = line.drop(separator + 1).trim()
            val name = SearchText.normalise(raw)

            check(code in labels) { "$where : le code CIQUAL $code n'existe pas dans la table." }
            check(name.isNotEmpty()) { "$where : nom courant vide [$raw]." }
            rejectRepeated(where, code, name)
            rejectLabel(where, code, name)

            seen[name] = lineNumber to code
            return CiqualCommonName(code = code, name = name)
        }

        private fun rejectRepeated(where: String, code: String, name: String) {
            val (earlierLine, earlierCode) = seen[name] ?: return
            error(
                "$where : le nom [$name] est deja donne au code $earlierCode, ligne $earlierLine ; " +
                    "un nom ne designe qu'une fiche (code $code ici).",
            )
        }

        private fun rejectLabel(where: String, code: String, name: String) {
            val owners = codesByLabel[name] ?: return
            val others = owners - code
            check(others.isEmpty()) {
                "$where : le nom [$name] est le libelle du code ${others.sorted().joinToString()} ; " +
                    "les fiches seraient a egalite."
            }
            error("$where : le nom [$name] est deja le libelle du code $code, il ne sert a rien.")
        }
    }
}
