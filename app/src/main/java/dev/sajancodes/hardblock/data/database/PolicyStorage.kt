package dev.sajancodes.hardblock.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "policies")
data class PolicyRecord(@PrimaryKey val id: String, val original: String, val current: String, val revision: Long)
@Entity(tableName = "policy_history")
data class PolicyHistoryEntity(@PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val policyId: String, val oldConfiguration: String?, val proposed: String?, val event: String, val reason: String, val at: Long)
@Entity(tableName = "relaxation_requests")
data class RelaxationRequestEntity(@PrimaryKey val id: String, val policyId: String, val revision: Long,
    val proposed: String, val requestedAt: Long, val availableAt: Long, val status: String)
@Entity(tableName = "local_state")
data class LocalStateEntity(@PrimaryKey val key: String, val value: String)
@Entity(tableName = "app_exceptions")
data class AppExceptionEntity(@PrimaryKey val packageName: String)

@Dao
interface PolicyDao {
    @Query("SELECT * FROM policies ORDER BY id") fun observe(): Flow<List<PolicyRecord>>
    @Query("SELECT * FROM policies ORDER BY id") suspend fun all(): List<PolicyRecord>
    @Query("SELECT * FROM policies WHERE id = :id") suspend fun find(id: String): PolicyRecord?
    @Upsert suspend fun save(record: PolicyRecord)
    @Insert suspend fun history(entry: PolicyHistoryEntity)
    @Query("SELECT * FROM policy_history ORDER BY sequence DESC LIMIT 100") suspend fun history(): List<PolicyHistoryEntity>
    @Query("SELECT * FROM relaxation_requests ORDER BY requestedAt DESC") fun requests(): Flow<List<RelaxationRequestEntity>>
    @Query("SELECT * FROM relaxation_requests WHERE id = :id") suspend fun request(id: String): RelaxationRequestEntity?
    @Upsert suspend fun saveRequest(request: RelaxationRequestEntity)
    @Query("UPDATE relaxation_requests SET status = 'CANCELLED' WHERE policyId = :id AND status = 'PENDING'") suspend fun invalidateRequests(id: String)
    @Query("SELECT * FROM local_state WHERE `key` = :key") suspend fun local(key: String): LocalStateEntity?
    @Upsert suspend fun local(value: LocalStateEntity)
    @Query("SELECT packageName FROM app_exceptions") suspend fun protectedApps(): List<String>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun protect(app: AppExceptionEntity)
}
