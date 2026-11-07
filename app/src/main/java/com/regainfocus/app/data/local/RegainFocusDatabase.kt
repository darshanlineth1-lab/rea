package com.regainfocus.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        FocusSessionEntity::class,
        BlockingGroupEntity::class,
        AppDailyLimitEntity::class,
        FocusScheduleEntity::class,
        HabitEntity::class,
        HabitCompletionEntity::class,
        InterceptionEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(RegainRoomConverters::class)
abstract class RegainFocusDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun blockingGroupDao(): BlockingGroupDao
    abstract fun appLimitDao(): AppLimitDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun habitDao(): HabitDao
    abstract fun interceptionDao(): InterceptionDao

    companion object {
        @Volatile
        private var INSTANCE: RegainFocusDatabase? = null

        fun getInstance(context: Context): RegainFocusDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RegainFocusDatabase::class.java,
                    "regain_focus.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
