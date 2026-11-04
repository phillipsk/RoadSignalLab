package io.roadsignal.sdk.internal

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.roadsignal.core.*
import io.roadsignal.sdk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal object StoreChanges {
    val revision = MutableStateFlow(0L)

    fun changed() {
        revision.update { it + 1 }
    }
}

internal data class StoredState(
    val consent: Boolean,
    val epoch: Long,
    val trips: List<TripSummary>,
    val pending: Int,
    val dead: Int,
    val receipts: Int,
    val unreadable: Int,
    val simulation: UploadSimulation,
)

/**
 * Process-local lock + SQLite transaction serialize privacy, persistence and mock acknowledgments.
 * Not multi-process.
 */
internal class TripStore(context: Context, databaseName: String = "roadsignal-v1.db") :
    AutoCloseable {
    private val helper = Database(context.applicationContext, databaseName)
    private val cipher = CipherBox()
    private val db
        get() = helper.writableDatabase

    private fun value(key: String, default: String): String =
        db.rawQuery("SELECT value FROM metadata WHERE key=?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) else default
        }

    private fun put(key: String, value: String) {
        db.insertWithOnConflict(
            "metadata",
            null,
            ContentValues().apply {
                put("key", key)
                put("value", value)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun count(table: String, where: String = ""): Int =
        db.rawQuery("SELECT count(*) FROM $table $where", null).use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun <T> transaction(block: () -> T): T =
        synchronized(LOCK) {
            db.beginTransaction()
            try {
                val result = block()
                db.setTransactionSuccessful()
                result
            } finally {
                db.endTransaction()
                StoreChanges.changed()
            }
        }

    fun token(): Pair<Boolean, Long> =
        synchronized(LOCK) { value("consent", "false").toBoolean() to value("epoch", "0").toLong() }

    fun read(): StoredState =
        synchronized(LOCK) {
            var bad = 0
            val trips = mutableListOf<TripSummary>()
            db.rawQuery("SELECT payload FROM trips ORDER BY sequence DESC", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val item = runCatching {
                        TripJson.decode(cipher.decrypt(cursor.getBlob(0)))
                    }
                        .getOrNull()
                    if (item == null) bad++ else trips += item
                }
            }
            StoredState(
                value("consent", "false").toBoolean(),
                value("epoch", "0").toLong(),
                trips,
                count("outbox", "WHERE state='pending'"),
                count("outbox", "WHERE state='dead'"),
                count("receipts"),
                bad,
                UploadSimulation.valueOf(value("simulation", UploadSimulation.ACKNOWLEDGE.name)),
            )
        }

    fun grantConsent() = transaction { put("consent", "true") }

    fun revokeAndDelete() = transaction {
        put("consent", "false")
        put("epoch", (value("epoch", "0").toLong() + 1).toString())
        db.delete("outbox", null, null)
        db.delete("trips", null, null)
        db.delete("receipts", null, null)
    }

    fun save(trip: TripSummary, expectedEpoch: Long): Boolean = transaction {
        if (value("consent", "false") != "true" || value("epoch", "0").toLong() != expectedEpoch)
            return@transaction false
        // Retain at most 50 trips. Evict only acknowledged history, never silently drop pending
        // data.
        if (count("trips") >= 50)
            db.execSQL(
                "DELETE FROM trips WHERE id IN (SELECT t.id FROM trips t LEFT JOIN outbox o ON t.id=o.trip_id WHERE o.trip_id IS NULL ORDER BY t.sequence LIMIT 1)",
            )
        if (count("trips") >= 50) return@transaction false
        val values =
            ContentValues().apply {
                put("id", trip.id)
                put("payload", cipher.encrypt(TripJson.encode(trip)))
                put("recorded_at_ms", System.currentTimeMillis())
            }
        val inserted =
            db.insertWithOnConflict("trips", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        if (inserted == -1L) return@transaction true
        db.insertOrThrow(
            "outbox",
            null,
            ContentValues().apply {
                put("trip_id", trip.id)
                put("attempts", 0)
                put("state", "pending")
            },
        )
        true
    }

    fun classify(id: String, role: TripRole) = transaction {
        if (value("consent", "false") != "true") return@transaction
        db.rawQuery("SELECT payload FROM trips WHERE id=?", arrayOf(id)).use {
            if (it.moveToFirst()) {
                val trip = TripJson.decode(cipher.decrypt(it.getBlob(0))).copy(role = role)
                db.update(
                    "trips",
                    ContentValues().apply { put("payload", cipher.encrypt(TripJson.encode(trip))) },
                    "id=?",
                    arrayOf(id),
                )
            }
        }
    }

    fun setSimulation(mode: UploadSimulation) = transaction { put("simulation", mode.name) }

    fun retryDeadLetters() = transaction {
        db.execSQL("UPDATE outbox SET state='pending', attempts=0 WHERE state='dead'")
    }

    /** Entirely local server simulation: no route, identity or telemetry is transmitted. */
    fun sync(): SyncReport = transaction {
        if (value("consent", "false") != "true") return@transaction SyncReport(0, 0, 0, true)
        val mode = UploadSimulation.valueOf(value("simulation", UploadSimulation.ACKNOWLEDGE.name))
        var ack = 0
        var retry = 0
        var dead = 0
        val entries = mutableListOf<Pair<String, Int>>()
        db.rawQuery("SELECT trip_id,attempts FROM outbox WHERE state='pending' LIMIT 20", null)
            .use { while (it.moveToNext()) entries += it.getString(0) to it.getInt(1) }
        for ((id, attempts) in entries) {
            val result =
                when (mode) {
                    UploadSimulation.OFFLINE -> DeliveryResult.RETRYABLE
                    UploadSimulation.RETRY_ONCE ->
                        if (attempts == 0) DeliveryResult.RETRYABLE else DeliveryResult.ACK
                    UploadSimulation.REJECT -> DeliveryResult.PERMANENT_FAILURE
                    UploadSimulation.ACKNOWLEDGE -> DeliveryResult.ACK
                }
            when (RetryPolicy.decide(result, attempts)) {
                DeliveryAction.ACKNOWLEDGE -> {
                    db.insertWithOnConflict(
                        "receipts",
                        null,
                        ContentValues().apply { put("trip_id", id) },
                        SQLiteDatabase.CONFLICT_IGNORE,
                    )
                    db.delete("outbox", "trip_id=?", arrayOf(id))
                    ack++
                }
                DeliveryAction.RETRY -> {
                    db.execSQL("UPDATE outbox SET attempts=attempts+1 WHERE trip_id=?", arrayOf(id))
                    retry++
                }
                DeliveryAction.DEAD_LETTER -> {
                    db.execSQL(
                        "UPDATE outbox SET state='dead',attempts=attempts+1 WHERE trip_id=?",
                        arrayOf(id),
                    )
                    dead++
                }
            }
        }
        // Receipt retention is bounded; there is no long-lived identifier or production backend
        // here.
        db.execSQL(
            "DELETE FROM receipts WHERE rowid NOT IN (SELECT rowid FROM receipts ORDER BY rowid DESC LIMIT 100)",
        )
        SyncReport(ack, retry, dead)
    }

    override fun close() = synchronized(LOCK) { helper.close() }

    private class Database(context: Context, name: String) :
        SQLiteOpenHelper(context, name, null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
            db.execSQL(
                "CREATE TABLE trips(sequence INTEGER PRIMARY KEY AUTOINCREMENT,id TEXT NOT NULL UNIQUE,payload BLOB NOT NULL,recorded_at_ms INTEGER NOT NULL DEFAULT 0)",
            )
            db.execSQL(
                "CREATE TABLE outbox(trip_id TEXT PRIMARY KEY,attempts INTEGER NOT NULL,state TEXT NOT NULL)",
            )
            db.execSQL("CREATE TABLE receipts(trip_id TEXT PRIMARY KEY)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion == 1 && newVersion == 2)
                db.execSQL("ALTER TABLE trips ADD COLUMN recorded_at_ms INTEGER NOT NULL DEFAULT 0")
            else error("A reviewed migration is required; destructive fallback is forbidden")
        }
    }

    companion object {
        private val LOCK = Any()
    }
}
