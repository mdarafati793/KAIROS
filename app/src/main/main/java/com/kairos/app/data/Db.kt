package com.kairos.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,          // reminder | task | date | study
    val title: String,
    val time: Long = 0,
    val done: Boolean = false
)

@Entity data class Memo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,          // user | model
    val text: String,
    val ts: Long = System.currentTimeMillis()
)

@Dao interface KDao {
    @Query("SELECT * FROM Item ORDER BY CASE WHEN time=0 THEN 1 ELSE 0 END, time") fun items(): Flow<List<Item>>
    @Query("SELECT * FROM Item WHERE time>:now AND done=0 AND kind!='study'") suspend fun upcoming(now: Long): List<Item>
    @Insert suspend fun add(i: Item): Long
    @Update suspend fun upd(i: Item)
    @Delete suspend fun del(i: Item)
    @Insert suspend fun addMemo(m: Memo)
    @Query("SELECT * FROM Memo ORDER BY id DESC LIMIT :n") suspend fun lastMemos(n: Int): List<Memo>
    @Query("DELETE FROM Memo") suspend fun clearMemos()
}

@Database(entities = [Item::class, Memo::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): KDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "kairos.db").build().also { inst = it }
        }
    }
}
