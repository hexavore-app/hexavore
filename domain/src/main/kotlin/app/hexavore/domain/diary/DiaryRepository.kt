package app.hexavore.domain.diary

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Le journal alimentaire, en lecture et en écriture.
 *
 * Port au sens de [docs/06][archi] : le domaine déclare ce dont il a besoin, et un
 * adaptateur le fournit — en mémoire pour les tests, depuis Room dans
 * l'application. Basculer de l'un à l'autre ne touche qu'une ligne du module Hilt,
 * et aucun appelant.
 *
 * L'observation rend un [Flow] — Room notifie ses invalidations, donc aucun écran
 * n'a à se rafraîchir lui-même. Les écritures sont `suspend` : un port d'écriture
 * qui rendrait un flux mélangerait commande et observation.
 *
 * **Une seule interface, et pas encore de découpage.** La ségrégation de
 * [docs/06][archi] concerne les interfaces vues par les clients ; ici, les deux
 * écrans qui s'en servent utilisent chacun la lecture et au moins une écriture.
 * Découper maintenant produirait deux interfaces implémentées par la même classe
 * pour aucun appelant allégé. Le jour où un client dépendra de méthodes qu'il
 * n'appelle pas, ce sera le signal.
 *
 * [archi]: docs/06-architecture.md
 */
interface DiaryRepository {
    /**
     * Les plats d'une journée, du plus ancien au plus récent.
     *
     * Une journée sans aucune saisie rend une **liste vide**, et non des plats à
     * zéro. Confondre les deux fausserait le calendrier et l'adaptation
     * hebdomadaire, qui doivent distinguer « rien noté » de « rien mangé ».
     */
    fun observeDay(date: LocalDate): Flow<List<Dish>>

    /**
     * Les plats d'une plage de jours, bornes incluses.
     *
     * **Les plats entiers, pas des totaux.** Agreger en base traiterait les valeurs
     * inconnues comme absentes, ce qui est juste arithmetiquement mais perd la trace
     * de ce qui manquait ([D29][decisions]) -- et c'est le domaine qui totalise, en
     * retenant quels totaux sont minores. Quelques centaines de lignes pour un mois.
     *
     * [decisions]: docs/11-decisions.md
     */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<Dish>>

    /** Un plat et ses lignes, ou `null` s'il n'existe plus. */
    suspend fun dish(id: DishId): Dish?

    /**
     * Écrit un plat et **remplace** entièrement ses lignes.
     *
     * Un seul verbe pour créer, modifier et restaurer, parce que les trois
     * demandent exactement la même chose de la base : que le plat et ses lignes
     * soient, après l'appel, ce que dit l'argument. Trois méthodes auraient donné
     * trois occasions d'écrire trois transactions légèrement différentes.
     *
     * L'opération est atomique : une coupure au milieu laisserait sinon un plat
     * sans contenu, visible à l'accueil et impossible à distinguer d'une saisie
     * réelle à zéro calorie.
     */
    suspend fun save(dish: Dish)

    /** Retire un plat et, avec lui, toutes ses lignes. */
    suspend fun deleteDish(id: DishId)

    /**
     * Détache de [favorite] les plats déjà enregistrés qui le citaient.
     *
     * **Le journal ne se réécrit pas, mais une provenance peut cesser d'être vraie.**
     * Un plat marqué « rejoué depuis les Flocons du matin » alors que ce favori a
     * changé de contenu annoncerait une origine qu'on ne peut plus vérifier : le lien
     * tombe, les lignes du plat restent intactes.
     *
     * C'est l'inverse d'une répercussion en chaîne, et c'est voulu : modifier un
     * modèle ne touche à aucun repas déjà noté ([D05][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    suspend fun unlinkFavorite(favorite: FavoriteDishId)
}
