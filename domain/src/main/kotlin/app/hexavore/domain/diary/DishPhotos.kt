package app.hexavore.domain.diary

import kotlinx.coroutines.flow.Flow

/**
 * Les photos des plats : celle qui accompagne le brouillon en cours, et celles des
 * plats déjà notés.
 *
 * **Un port qui parle en chemins et non en octets.** Une photo de repas pèse deux
 * cents kilo-octets ; l'accueil en montre cinq à la fois, et l'écran de validation en
 * agrandit une. Rendre des tableaux d'octets obligerait chacun à porter une image
 * entière en mémoire pour l'afficher au huitième de sa taille, alors que le décodeur
 * d'Android sait n'en lire qu'un pixel sur huit à partir d'un fichier. Le domaine ne
 * lit ni n'écrit ce fichier : il en désigne un.
 *
 * **Le brouillon dépose avant de savoir sous quel nom ranger.** Une analyse ou un scan
 * produisent leur image avant que le plat existe, donc avant que son identifiant
 * existe. L'image attend dans un emplacement unique, et [attach] la range quand
 * l'enregistrement a donné un identifiant. Un fichier plutôt qu'un objet en mémoire :
 * il survit à un processus tué entre les deux écrans, comme le reste du brouillon.
 *
 * **Rien n'est effacé quand un plat est supprimé**, et c'est délibéré : la suppression
 * est rattrapable par une barre d'annulation ([docs/02][parcours]), et une photo
 * effacée dans l'intervalle ne reviendrait pas. Ce sont [sweep] et le démarrage qui
 * ramassent les orphelines.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * @see docs/09-donnees-et-sauvegarde.md
 */
interface DishPhotos {
    /**
     * Les plats qui ont une photo, et où elle se trouve.
     *
     * Le tout et non la journée regardée : le flux est relu à chaque écriture, et un
     * flux paramétré par une liste de plats qui change à chaque changement de jour
     * serait plus coûteux à composer qu'une carte de quelques centaines d'entrées.
     */
    fun observeKept(): Flow<Map<DishId, PhotoFile>>

    /** La photo d'un plat, ou `null`. */
    suspend fun photoOf(dish: DishId): PhotoFile?

    /** L'image déposée par le brouillon en cours, ou `null`. */
    suspend fun staged(): PhotoFile?

    /** Dépose l'image du brouillon en cours, et remplace celle qui y était. */
    suspend fun stage(jpeg: ByteArray)

    /** Oublie l'image déposée. Sans effet s'il n'y en a pas. */
    suspend fun discardStaged()

    /**
     * Range l'image déposée sous le nom du plat.
     *
     * Sans effet si rien n'est déposé : c'est ce qui permet à l'écran de validation
     * d'appeler ce verbe sans distinguer les plats qui arrivent avec une image de ceux
     * qui arrivent sans.
     */
    suspend fun attach(dish: DishId)

    /** Retire la photo d'un plat. */
    suspend fun forget(dish: DishId)

    /** Retire toutes les photos, et l'image déposée avec. */
    suspend fun forgetAll()

    /**
     * Retire les photos dont le plat ne figure pas dans [known].
     *
     * Appelé au démarrage, et là seulement. Le faire après une suppression de plat
     * viderait la barre d'annulation de son sens ; le faire après une restauration
     * empêcherait de revenir à la copie de sécurité avec ses images.
     */
    suspend fun sweep(known: Set<DishId>)

    /** Ce que les photos occupent, pour l'écran des réglages. */
    suspend fun weight(): PhotoWeight
}

/**
 * Où lire une photo.
 *
 * Un chemin de fichier, que le domaine traverse sans l'ouvrir. Il n'est jamais
 * construit ici : seul l'adaptateur sait où il range, et lui seul en fabrique.
 */
@JvmInline
value class PhotoFile(val path: String)

/** Combien de photos, et combien elles pèsent. */
data class PhotoWeight(val count: Int, val bytes: Long) {
    companion object {
        val NOTHING = PhotoWeight(count = 0, bytes = 0L)
    }
}

/**
 * Garde-t-on les photos ?
 *
 * **Allumé par défaut**, et c'est ce qui a fait tomber la quatrième contrainte ferme de
 * [docs/01][perimetre] — « aucune photo n'est conservée ». Elle disait deux choses à la
 * fois : que rien ne part, et que rien ne reste. La première tient toujours et elle
 * était la seule qui protégeait quelqu'un ; la seconde effaçait, au nom de la
 * confidentialité, une image que l'utilisateur voulait garder sur son propre téléphone
 * ([D127][decisions]).
 *
 * **Son propre réglage, et non une case de l'apparence** : ce qu'il décide n'est pas ce
 * qu'on voit mais ce qui s'écrit sur le disque, et l'écran qui le porte montre aussi ce
 * que les photos pèsent et permet de les effacer.
 *
 * [perimetre]: docs/01-perimetre.md
 * [decisions]: docs/11-decisions.md
 */
interface PhotoSettings {
    fun observeKeeping(): Flow<Boolean>

    /** L'état courant, pour qui décide d'écrire ou non sans observer. */
    suspend fun keeping(): Boolean

    suspend fun setKeeping(keep: Boolean)
}
