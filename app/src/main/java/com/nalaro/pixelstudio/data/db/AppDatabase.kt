package com.nalaro.pixelstudio.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.nalaro.pixelstudio.data.models.ModelEntity
import com.nalaro.pixelstudio.data.models.ModelStatus

class ModelStatusConverter {
    @TypeConverter
    fun fromStatus(status: ModelStatus?): String {
        return (status ?: ModelStatus.NOT_INSTALLED).name
    }

    @TypeConverter
    fun toStatus(value: String?): ModelStatus {
        if (value.isNullOrBlank()) return ModelStatus.NOT_INSTALLED
        return try {
            ModelStatus.valueOf(value)
        } catch (_: IllegalArgumentException) {
            ModelStatus.NOT_INSTALLED
        }
    }
}

@Database(
    entities = [ModelEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(ModelStatusConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun modelDao(): ModelDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pixel_studio.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
