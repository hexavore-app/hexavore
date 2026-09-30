package app.hexavore.domain.report

/**
 * Un rapport que l'utilisateur **choisit** d'envoyer.
 *
 * ### Rien ne part tout seul
 *
 * [01][perimetre] l'écrit en contrainte ferme : zéro collecte, pas de rapport de
 * plantage automatique. Ces deux rapports ne la contredisent pas — ils la respectent
 * exactement ([D138][decisions]) : l'application **prépare** un courriel, l'ouvre dans
 * l'application de messagerie, et s'arrête là. Ce qui part, part parce que quelqu'un a
 * appuyé sur « envoyer » en ayant lu ce qu'il envoyait.
 *
 * C'est aussi pourquoi il n'y a pas de serveur : il n'y en a jamais eu, et un rapport
 * d'anomalie n'est pas une raison d'en ouvrir un.
 *
 * [perimetre]: docs/01-perimetre.md
 * [decisions]: docs/11-decisions.md
 */
data class Report(
    val subject: String,
    /**
     * Ce que le courriel porte en clair.
     *
     * **Lisible, et relu avant l'envoi** : c'est le seul moment où quelqu'un peut voir
     * ce qu'il s'apprête à transmettre, donc le refuser.
     */
    val body: String,
    /** Ce qui voyage à côté du texte — une photo, un échange trop long pour le corps. */
    val attachments: List<Attachment> = emptyList(),
)

/**
 * Une pièce jointe, en mémoire.
 *
 * Le domaine ne connaît ni fichier ni `Uri` : il dit ce qu'il y a à joindre et sous
 * quel nom. Où cela s'écrit pour traverser jusqu'à une application de messagerie
 * regarde l'adaptateur.
 */
data class Attachment(val name: String, val mimeType: String, val bytes: ByteArray) {
    /**
     * **Comparée par son contenu**, comme n'importe quelle donnée.
     *
     * Une `data class` qui porte un `ByteArray` compare les références : deux pièces
     * identiques ne seraient jamais égales, et une même pièce le serait toujours. Ce
     * type voyage dans des états d'écran, donc l'égalité doit dire quelque chose.
     */
    override fun equals(other: Any?): Boolean = this === other ||
        (other is Attachment && name == other.name && mimeType == other.mimeType && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = (name.hashCode() * HASH_STEP + mimeType.hashCode()) * HASH_STEP +
        bytes.contentHashCode()

    private companion object {
        const val HASH_STEP = 31
    }
}

/**
 * Ouvre un courriel prérempli, et rien de plus.
 *
 * **Un port, parce que rien de cela n'est du métier** : composer une intention, écrire
 * un fichier temporaire, en donner l'accès à une autre application. Le domaine sait
 * seulement qu'un rapport se propose.
 */
fun interface ReportSender {
    /** @return `false` si aucune application de messagerie n'a répondu. */
    suspend fun propose(report: Report): Boolean
}

/** L'adresse à laquelle ces rapports arrivent. Écrite une fois, ici. */
const val REPORT_ADDRESS = "contact@hexavore.app"
