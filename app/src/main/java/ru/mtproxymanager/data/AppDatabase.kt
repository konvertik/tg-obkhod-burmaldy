package ru.mtproxymanager.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ProxyEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun proxyDao(): ProxyDao
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE proxies ADD COLUMN failCount INTEGER")
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE proxies ADD COLUMN kind TEXT")
        db.execSQL("ALTER TABLE proxies ADD COLUMN protocolOk INTEGER")
        db.execSQL("ALTER TABLE proxies ADD COLUMN username TEXT")
        db.execSQL("ALTER TABLE proxies ADD COLUMN password TEXT")
    }
}
