package com.xycz.bilibili_live.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.xycz.bilibili_live.data.local.dao.FollowUserDao
import com.xycz.bilibili_live.data.local.dao.HistoryDao
import com.xycz.bilibili_live.data.local.entity.FollowUser
import com.xycz.bilibili_live.data.local.entity.History

/**
 * 应用数据库
 */
@Database(
    entities = [FollowUser::class, History::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun followUserDao(): FollowUserDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bilibili_live_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
