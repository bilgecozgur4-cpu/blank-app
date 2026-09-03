package com.metehan.assistant

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class MemoryLine(val role: String, val content: String)

class LocalMemoryDb(context: Context) : SQLiteOpenHelper(context, "metehan_native.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE memory (id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun add(role: String, content: String) {
        if (content.isBlank()) return
        writableDatabase.execSQL("INSERT INTO memory(role, content, created_at) VALUES(?,?,?)", arrayOf(role, content.take(12000), System.currentTimeMillis()))
    }
    fun recent(limit: Int = 12): List<MemoryLine> {
        val rows = mutableListOf<MemoryLine>()
        readableDatabase.rawQuery("SELECT role, content FROM memory ORDER BY id DESC LIMIT ?", arrayOf(limit.coerceIn(1,40).toString())).use { c ->
            while (c.moveToNext()) rows += MemoryLine(c.getString(0), c.getString(1))
        }
        return rows.asReversed()
    }
    fun clearAll() { writableDatabase.delete("memory", null, null) }
}
