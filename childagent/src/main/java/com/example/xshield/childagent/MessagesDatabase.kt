package com.example.xshield.childagent

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class InstantMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val app: String,
    val sender: String,
    val message: String,
    val direction: String,
    val timestamp: Long,
    val syncStatus: Int = 0 // 0 = unsynced, 1 = synced
)

class MessagesDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "messages_cache.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_MESSAGES = "messages"
        
        private const val COLUMN_ID = "id"
        private const val COLUMN_APP = "app"
        private const val COLUMN_SENDER = "sender"
        private const val COLUMN_MESSAGE = "message"
        private const val COLUMN_DIRECTION = "direction"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_SYNC_STATUS = "syncStatus"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE $TABLE_MESSAGES ("
                + "$COLUMN_ID TEXT PRIMARY KEY,"
                + "$COLUMN_APP TEXT,"
                + "$COLUMN_SENDER TEXT,"
                + "$COLUMN_MESSAGE TEXT,"
                + "$COLUMN_DIRECTION TEXT,"
                + "$COLUMN_TIMESTAMP INTEGER,"
                + "$COLUMN_SYNC_STATUS INTEGER" + ")")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
        onCreate(db)
    }

    fun addMessage(message: InstantMessage) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_ID, message.id)
        values.put(COLUMN_APP, message.app)
        values.put(COLUMN_SENDER, message.sender)
        values.put(COLUMN_MESSAGE, message.message)
        values.put(COLUMN_DIRECTION, message.direction)
        values.put(COLUMN_TIMESTAMP, message.timestamp)
        values.put(COLUMN_SYNC_STATUS, message.syncStatus)

        db.insert(TABLE_MESSAGES, null, values)
        db.close()
    }

    fun getUnsyncedMessages(): List<InstantMessage> {
        val messageList = mutableListOf<InstantMessage>()
        val selectQuery = "SELECT * FROM $TABLE_MESSAGES WHERE $COLUMN_SYNC_STATUS = 0 ORDER BY $COLUMN_TIMESTAMP ASC"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)

        if (cursor.moveToFirst()) {
            do {
                val message = InstantMessage(
                    id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    app = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_APP)),
                    sender = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SENDER)),
                    message = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE)),
                    direction = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DIRECTION)),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    syncStatus = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SYNC_STATUS))
                )
                messageList.add(message)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return messageList
    }

    fun markMessagesAsSynced(ids: List<String>) {
        if (ids.isEmpty()) return
        val db = this.writableDatabase
        val idList = ids.joinToString(",") { "'$it'" }
        db.execSQL("UPDATE $TABLE_MESSAGES SET $COLUMN_SYNC_STATUS = 1 WHERE $COLUMN_ID IN ($idList)")
        db.close()
    }

    fun deleteOldMessages() {
        val db = this.writableDatabase
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        db.delete(TABLE_MESSAGES, "$COLUMN_TIMESTAMP < ?", arrayOf(thirtyDaysAgo.toString()))
        db.close()
    }
}
