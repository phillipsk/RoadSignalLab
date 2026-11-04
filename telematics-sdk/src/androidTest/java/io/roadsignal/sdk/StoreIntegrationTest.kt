package io.roadsignal.sdk

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.roadsignal.core.*
import io.roadsignal.sdk.internal.*
import java.util.UUID
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoreIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "integration-${UUID.randomUUID()}.db"
    private lateinit var store: TripStore

    private fun trip(): TripSummary {
        val e = TripEngine()
        e.arm()
        return ReplayScenario.COMMUTE.samples().mapNotNull { e.accept(it).completed }.single()
    }

    @Before
    fun setup() {
        store = TripStore(context, name)
    }

    @After
    fun teardown() {
        store.close()
        context.deleteDatabase(name)
    }

    @Test
    fun consentGatesEncryptedPersistenceAndSurvivesReopen() {
        assertFalse(store.save(trip(), 0))
        store.grantConsent()
        val t = trip()
        assertTrue(store.save(t, 0))
        store.close()
        store = TripStore(context, name)
        assertEquals(t, store.read().trips.single())
        assertEquals(1, store.read().pending)
        SQLiteDatabase.openDatabase(
                context.getDatabasePath(name).path,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            .use { db ->
                db.rawQuery("SELECT payload FROM trips", null).use {
                    it.moveToFirst()
                    assertFalse(it.getBlob(0).toString(Charsets.UTF_8).contains(t.id))
                }
            }
    }

    @Test
    fun revocationAtomicallyInvalidatesTheOldEpoch() {
        store.grantConsent()
        store.save(trip(), 0)
        store.revokeAndDelete()
        assertFalse(store.read().consent)
        assertEquals(1, store.read().epoch)
        assertEquals(0, store.read().pending)
        store.grantConsent()
        assertFalse(store.save(trip(), 0))
        assertTrue(store.save(trip(), 1))
    }

    @Test
    fun retryThenAcknowledgeIsIdempotent() {
        store.grantConsent()
        val t = trip()
        store.save(t, 0)
        store.save(t, 0)
        assertEquals(1, store.read().pending)
        store.setSimulation(UploadSimulation.RETRY_ONCE)
        assertEquals(1, store.sync().retryable)
        assertEquals(1, store.sync().acknowledged)
        assertEquals(0, store.sync().acknowledged)
        assertEquals(1, store.read().receipts)
    }

    @Test
    fun permanentFailureRequiresExplicitRetry() {
        store.grantConsent()
        store.save(trip(), 0)
        store.setSimulation(UploadSimulation.REJECT)
        assertEquals(1, store.sync().deadLetters)
        assertEquals(1, store.read().dead)
        store.setSimulation(UploadSimulation.ACKNOWLEDGE)
        assertEquals(0, store.sync().acknowledged)
        store.retryDeadLetters()
        assertEquals(1, store.sync().acknowledged)
    }

    @Test
    fun offlineFailureBudgetIsFinite() {
        store.grantConsent()
        store.save(trip(), 0)
        store.setSimulation(UploadSimulation.OFFLINE)
        repeat(4) { assertEquals(1, store.sync().retryable) }
        assertEquals(1, store.sync().deadLetters)
        assertEquals(0, store.read().pending)
    }

    @Test
    fun consentRecheckBlocksWorkAfterRevoke() {
        store.grantConsent()
        store.save(trip(), 0)
        store.revokeAndDelete()
        assertTrue(store.sync().consentBlocked)
        assertEquals(0, store.read().receipts)
    }

    @Test
    fun v1ToV2MigrationPreservesEncryptedPayloadAndQueuedWork() {
        store.close()
        context.deleteDatabase(name)
        val t = trip()
        val payload = CipherBox().encrypt(TripJson.encode(t))
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
            db.execSQL(
                "CREATE TABLE trips(sequence INTEGER PRIMARY KEY AUTOINCREMENT,id TEXT NOT NULL UNIQUE,payload BLOB NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE outbox(trip_id TEXT PRIMARY KEY,attempts INTEGER NOT NULL,state TEXT NOT NULL)",
            )
            db.execSQL("CREATE TABLE receipts(trip_id TEXT PRIMARY KEY)")
            db.execSQL("INSERT INTO metadata VALUES('consent','true')")
            db.execSQL("INSERT INTO metadata VALUES('epoch','0')")
            db.insertOrThrow(
                "trips",
                null,
                ContentValues().apply {
                    put("id", t.id)
                    put("payload", payload)
                },
            )
            db.execSQL("INSERT INTO outbox VALUES(?,0,'pending')", arrayOf(t.id))
            db.version = 1
        }
        store = TripStore(context, name)
        assertEquals(t, store.read().trips.single())
        assertEquals(1, store.read().pending)
        assertEquals(1, store.sync().acknowledged)
    }

    @Test
    fun roleCorrectionPersistsLocally() {
        store.grantConsent()
        val t = trip()
        store.save(t, 0)
        store.classify(t.id, TripRole.PASSENGER)
        assertEquals(TripRole.PASSENGER, store.read().trips.single().role)
    }
}
