package app.hexavore.data.food

import app.hexavore.core.database.ciqual.CiqualDatabase
import app.hexavore.core.database.dao.FoodDao
import app.hexavore.core.database.dao.FoodMarksDao
import app.hexavore.core.database.entity.FoodEntity
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.food.FavoriteFoods
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodFilter
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodLookup
import app.hexavore.domain.food.FoodRanking
import app.hexavore.domain.food.FoodSearch
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.food.FoodStore
import app.hexavore.domain.food.FoodUsage
import app.hexavore.domain.food.RecentFoods
import app.hexavore.domain.food.SearchText
import app.hexavore.domain.identity.IdGenerator
import app.hexavore.domain.language.ContentLanguage
import app.hexavore.domain.language.ContentLanguages
import app.hexavore.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Le catalogue d'aliments, adossé aux deux bases.
 *
 * Une classe pour six ports, alors que le domaine les sépare : la séparation existe
 * pour que **les appelants** ne dépendent que de ce qu'ils utilisent ([docs/06][archi]),
 * pas pour forcer cinq objets. L'écran de recherche ne voit que [FoodSearch], et son
 * test n'a donc pas besoin d'un faux à quinze méthodes.
 *
 * Le septième, `BarcodeLookup`, vit dans [RoomBarcodeLookup] : c'est la seule lecture
 * qui ait à savoir que `source_ref` range deux espaces de noms dans une même colonne.
 *
 * **Une seule instance.** L'ouverture de la base de l'ANSES recopie un asset de
 * 1,6 Mo au premier appel ; cinq instances feraient cinq copies concurrentes du
 * même fichier.
 *
 * [archi]: docs/06-architecture.md
 */
@Singleton
class RoomFoodCatalog @Inject constructor(
    private val dao: FoodDao,
    private val marks: FoodMarksDao,
    private val ciqual: CiqualDatabase,
    private val languages: ContentLanguages,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : FoodSearch,
    FoodLookup,
    RecentFoods,
    FavoriteFoods,
    FoodStore,
    FoodUsage {
    /**
     * Le catalogue local d'abord, la table de l'ANSES ensuite, puis un seul tri.
     *
     * **Un aliment déjà copié n'apparaît pas deux fois.** Une fiche du catalogue qui
     * porte un code CIQUAL est la version vivante de la ligne de l'ANSES : c'est
     * elle qui compte les usages et porte les corrections, donc c'est elle qui reste.
     *
     * La table de l'ANSES est bornée par [limit] ; le catalogue local ne l'est pas,
     * parce qu'il est déjà borné par construction et que le classement doit voir
     * tous ses candidats. Le tri final en garde [limit].
     *
     * **Le flux vient du catalogue local, et lui seul rythme les ré-émissions.** Room
     * invalide sur écriture — épingler, supprimer, verser une fiche — et c'est ce qui
     * fait suivre les résultats sans qu'on relance la recherche ([D53][decisions]).
     * La table de l'ANSES, livrée en lecture seule, ne change jamais : la relire à
     * chaque invalidation coûte deux requêtes sur des libellés courts, et c'est ce
     * qui garde la fusion et le dédoublonnage justes quand une fiche vient d'être
     * copiée.
     *
     * **Une qualité demandée écarte la table de l'ANSES.** « Mon aliment » et
     * « Favori » ne peuvent désigner qu'une fiche déjà écrite : une ligne de la table
     * n'est ni personnelle ni épinglée tant qu'elle n'a pas été versée au catalogue
     * ([D54][decisions]). L'interroger quand même rendrait des résultats que le
     * filtre du domaine rejetterait juste après.
     *
     * [decisions]: docs/11-decisions.md
     */
    override fun search(query: String, filter: FoodFilter, limit: Int): Flow<List<Food>> {
        val normalised = SearchText.normalise(query)
        if (normalised.isBlank() && filter.isEmpty) return flowOf(emptyList())

        return dao
            .observeSearch(normalised)
            // Relue a chaque emission plutot qu'a la construction du flux : la
            // recherche reste ouverte pendant qu'on regle la langue, et Room reemet a
            // chaque ecriture. Une langue capturee une fois laisserait la liste dans
            // l'ancienne jusqu'a la fermeture de l'ecran.
            .map { rows -> ciqual.results(rows, normalised, query, filter, limit, ids, languages.current()) }
            // Les lectures de la base de l'ANSES : sans cela, elles se feraient sur
            // le dispatcher de celui qui collecte, c'est-a-dire le fil principal.
            .flowOn(dispatchers.io)
    }

    override suspend fun byId(id: FoodId): Food? = withContext(dispatchers.io) {
        dao.byId(id.value)?.let { ciqual.withServingsAndAnnotations(listOf(it), languages.current()).single() }
    }

    override fun observeRecent(limit: Int): Flow<List<Food>> = marks
        .observeRecent(limit)
        .map { ciqual.withServingsAndAnnotations(it, languages.current()) }
        .flowOn(dispatchers.io)

    override fun observeFavorites(): Flow<List<Food>> = marks
        .observeFavorites()
        .map { ciqual.withServingsAndAnnotations(it, languages.current()) }
        .flowOn(dispatchers.io)

    override suspend fun setFavorite(id: FoodId, favorite: Boolean) = withContext(dispatchers.io) {
        marks.setFavorite(id.value, favorite, clock.now().toEpochMilli())
    }

    /**
     * Le geste qui donne une existence durable a un aliment de la table de l ANSES.
     *
     * Un resultat de recherche non encore copie porte un identifiant provisoire :
     * l ecrire ici est ce qui le rend designable par une entree de journal. Sans ce
     * passage, une ligne pointerait vers une fiche absente, et la base la refuserait.
     */
    override suspend fun place(food: Food): Food = withContext(dispatchers.io) {
        // Par la reference plutot que par l identifiant : le provisoire change a
        // chaque recherche, alors que (source, source_ref) designe la meme chose
        // pour toujours. Chercher par identifiant recopierait l aliment a chaque
        // fois qu on le choisit.
        val reference = food.sourceRef
        val known = when {
            reference != null -> dao.byReference(food.source.name, reference)
            else -> dao.byId(food.id.value)
        }
        // Avec ses annotations : sans elles, la fiche rendue perdait son titre court
        // et, depuis que la table est bilingue, son libelle dans la langue en cours --
        // au moment precis ou l'ecran de validation va l'afficher.
        known?.let {
            return@withContext ciqual.withServingsAndAnnotations(listOf(it), languages.current()).single()
        }

        dao.upsert(food.toEntity(clock.now().toEpochMilli()))
        food
    }

    override suspend fun save(food: Food): FoodId = withContext(dispatchers.io) {
        dao.upsert(food.toEntity(clock.now().toEpochMilli()))
        food.id
    }

    override suspend fun delete(id: FoodId) = withContext(dispatchers.io) { dao.delete(id.value) }

    override suspend fun remember(foods: Collection<Food>, at: Instant): Map<FoodId, FoodId> =
        withContext(dispatchers.io) {
            foods.associate { food ->
                // La fiche n'est ecrite que si elle est absente : reecrire une fiche
                // connue defairait une correction apportee a un aliment personnel, avec
                // les valeurs qu'un brouillon ouvert depuis dix minutes porte encore.
                val stored = place(food)
                marks.markUsed(stored.id.value, at.toEpochMilli())
                food.id to stored.id
            }
        }
}

/**
 * Les deux provenances, filtrées puis classées ensemble.
 *
 * Hors de la classe, comme les trois fonctions qui suivent : ce sont des lectures de
 * la base embarquée, pas des capacités que le catalogue expose.
 */
private fun CiqualDatabase.results(
    rows: List<FoodEntity>,
    normalised: String,
    query: String,
    filter: FoodFilter,
    limit: Int,
    ids: IdGenerator,
    language: ContentLanguage,
): List<Food> {
    val local = withAnnotations(rows, language).filter(filter::matches)
    // Une qualite demandee ecarte la table de l'ANSES : une ligne qui n'a pas ete
    // versee au catalogue n'est ni personnelle ni epinglee.
    val proposed =
        if (filter.traits.isEmpty()) notYetCopied(local, normalised, filter, limit, ids, language) else emptyList()
    return FoodRanking.sort(local + proposed, query).take(limit)
}

/**
 * Les lignes de l'ANSES que le catalogue n'a pas encore copiées.
 *
 * Hors de la classe pour la même raison que [servingsOf] plus bas : c'est une lecture
 * de la base embarquée, pas une des six capacités que le catalogue expose.
 *
 * Chaque ligne non copiée reçoit un identifiant **provisoire**, régénéré à chaque
 * appel. Il ne désigne rien tant que [FoodStore.place] n'a pas été appelé, et c'est
 * volontaire : copier les 3 484 lignes à l'installation gonflerait la base, les
 * sauvegardes et la recherche avec 99 % de contenu jamais utilisé ([D51][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
private fun CiqualDatabase.notYetCopied(
    local: List<Food>,
    normalised: String,
    filter: FoodFilter,
    limit: Int,
    ids: IdGenerator,
    language: ContentLanguage,
): List<Food> {
    val alreadyCopied = local.mapNotNullTo(mutableSetOf()) { it.sourceRef.takeIf { _ -> it.isFromCiqual } }

    return search(normalised, filter.categories.mapTo(mutableSetOf()) { it.name }, limit, language.tag)
        .filterNot { it.code in alreadyCopied }
        .map { row -> row.toDomain(FoodId(ids.next()), servings(row.code, language.tag)) }
}

/**
 * Le rayon et le titre court des fiches copiées, relus dans la table de l'ANSES.
 *
 * **Ils ne sont pas stockés dans `food`, et c'est un choix.** Une copie figerait la
 * correspondance du jour où elle a été faite : corriger un rayon dans
 * `CiqualCategories`, ou un titre à rallonge dans `short-names.csv`, ne l'atteindrait
 * jamais — et une migration ne pourrait pas le rattraper, les deux bases étant deux
 * fichiers ([D54][decisions]). Ce sont des propriétés de la **référence**, pas de la
 * copie, contrairement aux six valeurs, que le journal fige exprès ([D05][decisions]).
 *
 * En un seul lot : une requête par ligne en ferait des centaines par affichage.
 *
 * [decisions]: docs/11-decisions.md
 */
private fun CiqualDatabase.withAnnotations(rows: List<FoodEntity>, language: ContentLanguage): List<Food> {
    val annotations = annotationsOf(rows.mapNotNull { it.ciqualCode }, language.tag)
    return rows.map { it.toDomain(annotations = annotations[it.ciqualCode]) }
}

/** Comme [withAnnotations], plus les portions — pour les listes courtes qui les affichent. */
private fun CiqualDatabase.withServingsAndAnnotations(rows: List<FoodEntity>, language: ContentLanguage): List<Food> {
    val annotations = annotationsOf(rows.mapNotNull { it.ciqualCode }, language.tag)
    return rows.map {
        it.toDomain(
            servings = servingsOf(it.source, it.sourceRef, language),
            annotations = annotations[it.ciqualCode],
        )
    }
}

/** Le code de l'ANSES d'une fiche, quand elle en vient. Lui seul désigne un rayon. */
private val FoodEntity.ciqualCode: String? get() = sourceRef.takeIf { source == FoodSource.CIQUAL.name }

/**
 * Les portions d'une fiche viennent de la table de l'ANSES, quand elle en vient.
 *
 * Hors de la classe : c'est une lecture de la base embarquée, pas une des six
 * capacités que le catalogue expose.
 */
private fun CiqualDatabase.servingsOf(source: String, reference: String?, language: ContentLanguage) =
    if (source == FoodSource.CIQUAL.name && reference != null) {
        servings(reference, language.tag).map { it.toDomain() }
    } else {
        emptyList()
    }

private val Food.isFromCiqual: Boolean get() = source == FoodSource.CIQUAL
