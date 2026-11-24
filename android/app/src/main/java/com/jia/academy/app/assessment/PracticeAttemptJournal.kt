package com.jia.academy.app.assessment
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
@Entity(tableName="practice_attempt_journal",indices=[Index(value=["sessionId","promptId"],unique=true),Index(value=["syncState","createdAt"])])
data class PracticeAttemptEntity(@PrimaryKey val eventId:String,val sessionId:String,val promptId:String,val selectedOption:Int,val isCorrect:Boolean,val elapsedMillis:Long,val createdAt:Long,val syncState:String="PENDING",val retryCount:Int=0)
@Dao interface PracticeAttemptDao {
 @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun append(event:PracticeAttemptEntity):Long
 @Query("SELECT * FROM practice_attempt_journal WHERE syncState='PENDING' ORDER BY createdAt LIMIT :limit") suspend fun pending(limit:Int=100):List<PracticeAttemptEntity>
 @Query("UPDATE practice_attempt_journal SET syncState='SYNCED' WHERE eventId=:eventId") suspend fun markSynced(eventId:String)
 @Query("UPDATE practice_attempt_journal SET retryCount=retryCount+1 WHERE eventId=:eventId") suspend fun recordRetry(eventId:String)
 @Query("SELECT COUNT(*) FROM practice_attempt_journal WHERE syncState='PENDING'") fun pendingCount():Flow<Int>
}
@Database(entities=[PracticeAttemptEntity::class],version=1,exportSchema=true)
abstract class PracticeAttemptDatabase:RoomDatabase(){abstract fun attempts():PracticeAttemptDao}
