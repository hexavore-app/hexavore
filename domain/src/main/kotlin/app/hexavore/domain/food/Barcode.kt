package app.hexavore.domain.food

/**
 * Un code-barres de produit alimentaire, sous sa forme canonique **EAN-13 ou EAN-8**.
 *
 * **Le constructeur est privé, et c'est tout l'objet de ce type.** Un `String` aurait
 * suffi à transporter la valeur ; il n'aurait pas garanti que deux lectures du même
 * produit donnent la même clé. Or c'est exactement ce dont dépend la promesse de la
 * tranche : *« le deuxième scan du même produit est instantané et fonctionne en mode
 * avion »*. Le catalogue local retrouve une fiche par son `source_ref` ; si la
 * première lecture y écrit douze chiffres et la seconde en cherche treize, le cache
 * ne sert jamais, et le défaut ne se voit qu'en mode avion.
 *
 * **UPC-A est EAN-13 avec un zéro devant.** La conversion est exacte, jamais ambiguë,
 * et elle ne change pas la clé de contrôle — le zéro ajouté tombe sur un poids 1.
 * C'est pour ça qu'elle est faite ici, une fois, et pas au cas par cas.
 *
 * **UPC-E n'est pas accepté**, et c'est un écart assumé avec [docs/02][parcours].
 * Huit chiffres ne disent pas s'ils sont un EAN-8 ou un UPC-E compressé, rien ne les
 * décompresse en amont, et lire un UPC-E comme un EAN-8 produirait un code **plausible
 * désignant un autre produit** — la pire des issues, puisqu'elle afficherait une
 * fiche fausse au lieu de dire « introuvable ». La clé de contrôle le rattraperait
 * neuf fois sur dix, ce qui est une coïncidence et non une règle. Le jour où un
 * UPC-E est réellement scanné, la symbologie descendra jusqu'ici, et la
 * décompression avec.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * @see docs/04-sources-de-donnees.md
 */
@JvmInline
value class Barcode private constructor(val value: String) {
    companion object {
        /**
         * Le code lu, ou `null` si ce n'en est pas un.
         *
         * Refuse plutôt que de corriger : une longueur inattendue ou une clé de
         * contrôle fausse veut dire qu'on n'a pas lu ce qu'on croit. Interroger Open
         * Food Facts avec un code inventé rendrait « produit introuvable » — une
         * réponse qui a l'air d'une information alors qu'elle est un défaut.
         */
        fun of(raw: String): Barcode? {
            val digits = raw.trim()
            val canonical = when {
                !digits.all { it.isDigit() } -> null
                digits.length == EAN_8_LENGTH || digits.length == EAN_13_LENGTH -> digits
                digits.length == UPC_A_LENGTH -> "0$digits"
                else -> null
            }

            return canonical
                ?.takeIf { code -> checkDigitOf(code) == code.last() }
                ?.let { code -> Barcode(code) }
        }

        private const val EAN_8_LENGTH = 8
        private const val UPC_A_LENGTH = 12
        private const val EAN_13_LENGTH = 13

        private const val ODD_WEIGHT = 3
        private const val EVEN_WEIGHT = 1
        private const val MODULUS = 10

        /**
         * La clé de contrôle attendue pour ces chiffres.
         *
         * Une seule fonction pour EAN-8 et EAN-13 : les poids se comptent **depuis la
         * droite**, donc ils ne dépendent pas de la longueur du code. C'est aussi ce
         * qui rend l'ajout d'un zéro à gauche inoffensif.
         */
        private fun checkDigitOf(code: String): Char {
            val sum = code
                .dropLast(1)
                .reversed()
                .mapIndexed { rank, digit ->
                    digit.digitToInt() * if (rank % 2 == 0) ODD_WEIGHT else EVEN_WEIGHT
                }.sum()

            return '0' + (MODULUS - sum % MODULUS) % MODULUS
        }
    }
}
