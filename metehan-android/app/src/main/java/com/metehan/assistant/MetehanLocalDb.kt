package com.metehan.assistant

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Locale

class MetehanLocalDb(context: Context) : SQLiteOpenHelper(context, "metehan_local.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE memory(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, text TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE chat(id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, text TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE audit(id INTEGER PRIMARY KEY AUTOINCREMENT, action_type TEXT NOT NULL, target TEXT NOT NULL, approved INTEGER NOT NULL, executed INTEGER NOT NULL, detail TEXT NOT NULL, created_at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun remember(text: String, kind: String = "user_note") {
        val clean = text.trim()
        if (clean.isBlank()) return
        writableDatabase.execSQL(
            "INSERT INTO memory(kind,text,created_at) VALUES(?,?,?)",
            arrayOf(kind, clean, System.currentTimeMillis()),
        )
    }

    fun recentMemories(limit: Int = 20): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT text FROM memory ORDER BY id DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 100).toString()),
        ).use { c -> while (c.moveToNext()) out += c.getString(0) }
        return out
    }

    fun relevantMemories(query: String, limit: Int = 8): List<String> {
        val locale = Locale("tr", "TR")
        val q = query.lowercase(locale).split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 }.toSet()
        val candidates = recentMemories(60)
        if (q.isEmpty()) return candidates.take(limit)
        return candidates
            .map { text ->
                val words = text.lowercase(locale).split(Regex("[^\\p{L}\\p{N}]+"))
                val score = words.count { it in q }
                score to text
            }
            .filter { it.first > 0 }
            .sortedByDescending { it.first }
            .take(limit)
            .map { it.second }
    }

    fun addChat(role: String, text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        writableDatabase.execSQL(
            "INSERT INTO chat(role,text,created_at) VALUES(?,?,?)",
            arrayOf(role, clean.take(12000), System.currentTimeMillis()),
        )
        writableDatabase.execSQL("DELETE FROM chat WHERE id NOT IN (SELECT id FROM chat ORDER BY id DESC LIMIT 120)")
    }

    fun recentChat(limit: Int = 10): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        readableDatabase.rawQuery(
            "SELECT role,text FROM chat ORDER BY id DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 30).toString()),
        ).use { c -> while (c.moveToNext()) out += c.getString(0) to c.getString(1) }
        return out.reversed()
    }

    fun logAction(action: AgentAction, approved: Boolean, executed: Boolean, detail: String) {
        writableDatabase.execSQL(
            "INSERT INTO audit(action_type,target,approved,executed,detail,created_at) VALUES(?,?,?,?,?,?)",
            arrayOf(action.type, action.target, if (approved) 1 else 0, if (executed) 1 else 0, detail.take(1000), System.currentTimeMillis()),
        )
    }

    fun clearConversation() {
        writableDatabase.execSQL("DELETE FROM chat")
    }
}
