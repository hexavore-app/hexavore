package app.hexavore.data.profile

import app.hexavore.core.database.dao.GoalDao
import app.hexavore.core.database.dao.ProfileDao
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.goal.Goal
import app.hexavore.domain.goal.Goals
import app.hexavore.domain.identity.IdGenerator
import app.hexavore.domain.profile.Profiles
import app.hexavore.domain.profile.UserProfile
import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.profile.WeightLog
import app.hexavore.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Le profil, le journal de poids et les objectifs, adossés à Room.
 *
 * Trois ports pour une classe, comme `RoomFoodCatalog` en tient six : la séparation
 * existe pour que **les appelants** ne dépendent que de ce qu'ils utilisent
 * ([docs/06][archi]), pas pour forcer trois objets. L'onboarding écrit les trois d'un
 * bloc, l'accueil ne lit que [Goals].
 *
 * [archi]: docs/06-architecture.md
 */
@Singleton
class RoomProfileStore @Inject constructor(
    private val profiles: ProfileDao,
    private val goalDao: GoalDao,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : Profiles,
    WeightLog,
    Goals {
    override fun observeProfile(): Flow<UserProfile?> =
        profiles.observeProfile().map { it?.toDomain() }.flowOn(dispatchers.io)

    override suspend fun save(profile: UserProfile) = withContext(dispatchers.io) {
        profiles.upsert(profile.toEntity(clock.now().toEpochMilli()))
    }

    override fun observeHistory(): Flow<List<WeightEntry>> =
        profiles.observeAllWeights().map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override fun observeLatest(): Flow<WeightEntry?> =
        profiles.observeLatestWeight().map { it?.toDomain() }.flowOn(dispatchers.io)

    /**
     * Une pesée par jour, la dernière remplace.
     *
     * La ligne du jour est retirée avant d'écrire la neuve plutôt que mise à jour :
     * l'index unique porte sur `date`, et un `upsert` par clé primaire ne le verrait
     * pas — il échouerait sur la contrainte au lieu de remplacer.
     */
    override suspend fun record(entry: WeightEntry) = withContext(dispatchers.io) {
        profiles.deleteWeightOn(entry.date.toString())
        profiles.upsertWeight(entry.toEntity(ids.next(), clock.now().toEpochMilli()))
    }

    /** La même requête que celle qui fait place à la pesée neuve du jour, seule. */
    override suspend fun delete(date: LocalDate) = withContext(dispatchers.io) {
        profiles.deleteWeightOn(date.toString())
    }

    override fun observeGoalOn(date: LocalDate): Flow<Goal?> =
        goalDao.observeGoalOn(date.toString()).map { it?.toDomain() }.flowOn(dispatchers.io)

    override fun observeCurrent(): Flow<Goal?> = goalDao.observeCurrent().map { it?.toDomain() }.flowOn(dispatchers.io)

    override fun observeAll(): Flow<List<Goal>> =
        goalDao.observeAllGoals().map { goals -> goals.map { it.toDomain() } }.flowOn(dispatchers.io)

    override suspend fun replace(goal: Goal) = withContext(dispatchers.io) {
        goalDao.replace(goal.toEntity(clock.now().toEpochMilli()))
    }
}
