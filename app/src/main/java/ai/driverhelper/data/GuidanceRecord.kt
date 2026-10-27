package ai.driverhelper.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** One spoken guidance. Position is rounded to 3 decimals (~100 m): enough to show where, not an exact track. */
@Entity(tableName = "guidance")
data class GuidanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: String,
    val timeMs: Long,
    val lat: Double?,
    val lon: Double?,
)

@Dao
interface GuidanceDao {
    @Insert
    suspend fun insert(record: GuidanceRecord)

    @Query("SELECT * FROM guidance WHERE timeMs >= :sinceMs ORDER BY timeMs")
    suspend fun since(sinceMs: Long): List<GuidanceRecord>
}

@Database(entities = [GuidanceRecord::class], version = 1, exportSchema = false)
abstract class GuidanceDatabase : RoomDatabase() {
    abstract fun dao(): GuidanceDao
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun dao(@ApplicationContext context: Context): GuidanceDao =
        Room.databaseBuilder(context, GuidanceDatabase::class.java, "guidance.db").build().dao()
}
