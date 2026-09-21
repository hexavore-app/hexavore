package app.hexavore.integration.scanner

import app.hexavore.domain.food.Barcode

/**
 * L'anti-rebond : un code n'est retenu qu'après **deux lectures identiques
 * consécutives**, et une seule fois.
 *
 * Sans lui, le décodeur rend une lecture par image — trente par seconde — et l'écran
 * clignote pendant que l'application redemande le même produit. La règle est dans
 * [docs/02][parcours] et elle a deux moitiés qu'on oublie facilement de distinguer :
 * **deux lectures d'accord** écartent la lecture douteuse, **puis on s'arrête**
 * jusqu'à ce que l'écran redemande. La seconde est ce qui empêche la rafale.
 *
 * **Une lecture que [Barcode] refuse ne compte pas, et casse la suite.** Elle n'est
 * pas « rien » : c'est une lecture, et deux codes valides séparés par une lecture
 * fausse n'ont pas été lus consécutivement. Les traiter autrement reviendrait à
 * accepter un accord que l'optique n'a pas donné.
 *
 * **Pur, sans caméra ni décodeur**, et c'est délibéré : c'est la seule partie du
 * scanner qu'on puisse éprouver sur la JVM, donc la seule qui doive porter une règle.
 *
 * **Rien n'y est synchronisé** : un seul fil s'en sert, celui de l'analyse, et
 * [CameraSession] y envoie jusqu'à la reprise.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
class SteadyBarcode {
    private var previous: Barcode? = null
    private var settled = false

    /**
     * Le code, quand cette lecture confirme la précédente. `null` sinon — y compris
     * après une confirmation, tant que [resume] n'a pas été appelé.
     */
    fun read(raw: String): Barcode? {
        if (settled) return null

        val code = Barcode.of(raw)
        val agreed = code != null && code == previous
        previous = code
        settled = agreed

        return code.takeIf { agreed }
    }

    /**
     * Rouvre la lecture, après que l'écran a fini avec le code précédent.
     *
     * **La mémoire est effacée**, et pas seulement le verrou : sans cela, la première
     * image qui suit une reprise confirmerait le code déjà traité, et rescanner le
     * même produit rendrait un résultat sans qu'on ait rien visé.
     */
    fun resume() {
        previous = null
        settled = false
    }
}
