package com.watercantracker.app.sync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.watercantracker.app.data.local.dao.MemberDao
import com.watercantracker.app.data.local.dao.PaymentDao
import com.watercantracker.app.data.local.entity.MemberEntity
import com.watercantracker.app.data.local.entity.PaymentEntity
import com.watercantracker.app.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncStatus { IDLE, SYNCING, SUCCESS, ERROR, DISABLED }

data class SyncState(
    val status: SyncStatus = SyncStatus.IDLE,
    val lastSyncAt: Long? = null,
    val roomId: String? = null,
    val isMaster: Boolean = true,
    val error: String? = null,
    val connectedDevices: Int = 0
)

@Singleton
class FirebaseSyncManager @Inject constructor(
    private val memberDao: MemberDao,
    private val paymentDao: PaymentDao,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Use getInstance() without a URL so it picks up the URL from google-services.json.
    // Providing a hardcoded URL while google-services.json has a different one causes auth
    // failures that manifest as the sync hanging indefinitely.
    private val db   = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState

    private var activeListener: ValueEventListener? = null
    private var activeRoomRef: com.google.firebase.database.DatabaseReference? = null

    // ── Auth ──────────────────────────────────────────────────────────────────

    /**
     * Sign in anonymously — gives each device a stable UID without requiring
     * users to create an account. Times out after 10 s so the UI never hangs.
     */
    private suspend fun ensureAuthenticated() {
        if (auth.currentUser != null) return
        try {
            withTimeout(10_000L) {
                auth.signInAnonymously().await()
            }
        } catch (e: Exception) {
            throw Exception("Authentication failed: ${e.message}")
        }
    }

    // ── Room management ───────────────────────────────────────────────────────

    /** Master device: create a new room, push existing data, return room ID. */
    suspend fun createRoom(): String {
        _syncState.update { it.copy(status = SyncStatus.SYNCING, error = null) }
        return try {
            ensureAuthenticated()
            val roomRef = db.reference.child("rooms").push()
            val roomId  = roomRef.key ?: throw Exception("Failed to allocate room key")

            withTimeout(10_000L) {
                roomRef.child("meta").setValue(
                    mapOf(
                        "masterDeviceId" to (auth.currentUser?.uid ?: "unknown"),
                        "createdAt"      to System.currentTimeMillis()
                    )
                ).await()
            }

            // Push local data — fire-and-forget with individual error handling
            pushAllToFirebase(roomId)

            settingsRepository.updateFirebaseRoom(roomId, isMaster = true)
            _syncState.update {
                it.copy(status = SyncStatus.SUCCESS, roomId = roomId,
                    isMaster = true, lastSyncAt = System.currentTimeMillis(), error = null)
            }
            startListening(roomId, isMaster = true)
            roomId
        } catch (e: Exception) {
            _syncState.update { it.copy(status = SyncStatus.ERROR, error = e.message) }
            throw e
        }
    }

    /** Secondary device: join an existing room by scanning the QR / pasting the ID. */
    suspend fun joinRoom(roomId: String) {
        _syncState.update { it.copy(status = SyncStatus.SYNCING, error = null) }
        try {
            ensureAuthenticated()
            val snapshot = withTimeout(15_000L) {
                db.reference.child("rooms").child(roomId).get().await()
            }
            if (!snapshot.exists()) throw Exception("Room \"$roomId\" not found. Check the ID and try again.")

            pullFromFirebase(snapshot)
            settingsRepository.updateFirebaseRoom(roomId, isMaster = false)
            _syncState.update {
                it.copy(status = SyncStatus.SUCCESS, roomId = roomId,
                    isMaster = false, lastSyncAt = System.currentTimeMillis(), error = null)
            }
            startListening(roomId, isMaster = false)
        } catch (e: Exception) {
            _syncState.update { it.copy(status = SyncStatus.ERROR, error = e.message) }
        }
    }

    // ── Push helpers (master only) ────────────────────────────────────────────

    fun pushPayment(roomId: String, payment: PaymentEntity) {
        if (!_syncState.value.isMaster) return
        scope.launch {
            try {
                val ref = if (payment.firebaseSyncId != null)
                    db.reference.child("rooms/$roomId/payments/${payment.firebaseSyncId}")
                else
                    db.reference.child("rooms/$roomId/payments").push()

                withTimeout(8_000L) { ref.setValue(paymentToMap(payment)).await() }
                ref.key?.let { key ->
                    if (payment.firebaseSyncId == null) {
                        paymentDao.updatePayment(payment.copy(firebaseSyncId = key))
                    }
                }
                touchMeta(roomId)
            } catch (e: Exception) {
                _syncState.update { it.copy(error = "Push failed: ${e.message}") }
            }
        }
    }

    fun pushMember(roomId: String, member: MemberEntity) {
        if (!_syncState.value.isMaster) return
        scope.launch {
            try {
                val ref = if (member.firebaseSyncId != null)
                    db.reference.child("rooms/$roomId/members/${member.firebaseSyncId}")
                else
                    db.reference.child("rooms/$roomId/members").push()

                withTimeout(8_000L) { ref.setValue(memberToMap(member)).await() }
                ref.key?.let { key ->
                    if (member.firebaseSyncId == null) {
                        memberDao.updateMember(member.copy(firebaseSyncId = key))
                    }
                }
                touchMeta(roomId)
            } catch (e: Exception) {
                _syncState.update { it.copy(error = "Push failed: ${e.message}") }
            }
        }
    }

    fun deletePayment(roomId: String, firebaseSyncId: String) {
        if (!_syncState.value.isMaster) return
        scope.launch {
            try {
                withTimeout(8_000L) {
                    db.reference.child("rooms/$roomId/payments/$firebaseSyncId").removeValue().await()
                }
                touchMeta(roomId)
            } catch (e: Exception) {
                _syncState.update { it.copy(error = "Delete failed: ${e.message}") }
            }
        }
    }

    // ── Realtime listener ─────────────────────────────────────────────────────

    fun startListening(roomId: String, isMaster: Boolean) {
        stopListening()
        val roomRef = db.reference.child("rooms").child(roomId)
        activeRoomRef = roomRef
        activeListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isMaster) {
                    // Secondary devices pull every change from Firebase → local Room
                    scope.launch {
                        try {
                            pullFromFirebase(snapshot)
                            _syncState.update {
                                it.copy(
                                    status     = SyncStatus.SUCCESS,
                                    lastSyncAt = System.currentTimeMillis(),
                                    error      = null
                                )
                            }
                        } catch (e: Exception) {
                            _syncState.update { it.copy(error = "Pull failed: ${e.message}") }
                        }
                    }
                } else {
                    // Master just updates its status badge
                    _syncState.update {
                        it.copy(status = SyncStatus.SUCCESS, lastSyncAt = System.currentTimeMillis())
                    }
                }
                val devices = snapshot.child("meta/connectedDevices")
                    .getValue(Long::class.java)?.toInt() ?: 1
                _syncState.update { it.copy(connectedDevices = devices) }
            }

            override fun onCancelled(error: DatabaseError) {
                _syncState.update {
                    it.copy(
                        status = SyncStatus.ERROR,
                        error  = "Sync cancelled: ${error.message}"
                    )
                }
            }
        }
        roomRef.addValueEventListener(activeListener!!)
    }

    fun stopListening() {
        activeListener?.let { activeRoomRef?.removeEventListener(it) }
        activeListener = null
        activeRoomRef  = null
    }

    fun disconnect() {
        stopListening()
        scope.launch { settingsRepository.clearFirebaseRoom() }
        _syncState.update { SyncState(status = SyncStatus.IDLE) }
    }

    // ── Data push / pull ──────────────────────────────────────────────────────

    private suspend fun pushAllToFirebase(roomId: String) {
        val members  = memberDao.getAllMembers()
        val payments = paymentDao.observeAllPayments().first()

        members.forEach { m ->
            try {
                val ref = db.reference.child("rooms/$roomId/members").push()
                withTimeout(8_000L) { ref.setValue(memberToMap(m)).await() }
                ref.key?.let { memberDao.updateMember(m.copy(firebaseSyncId = it)) }
            } catch (_: Exception) { /* skip individual failures */ }
        }
        payments.forEach { p ->
            try {
                val ref = db.reference.child("rooms/$roomId/payments").push()
                withTimeout(8_000L) { ref.setValue(paymentToMap(p)).await() }
                ref.key?.let { paymentDao.updatePayment(p.copy(firebaseSyncId = it)) }
            } catch (_: Exception) { /* skip individual failures */ }
        }
    }

    private suspend fun pullFromFirebase(snapshot: DataSnapshot) {
        // Members
        snapshot.child("members").children.forEach { memberSnap ->
            val fbKey  = memberSnap.key ?: return@forEach
            val name   = memberSnap.child("name").getValue(String::class.java) ?: return@forEach
            val phone  = memberSnap.child("phoneNumber").getValue(String::class.java)
            val active = memberSnap.child("isActive").getValue(Boolean::class.java) ?: true
            val order  = memberSnap.child("rotationOrder").getValue(Long::class.java)?.toInt() ?: 0

            val existing = memberDao.getAllMembers().firstOrNull { it.firebaseSyncId == fbKey }
            if (existing == null) {
                memberDao.insertMember(
                    MemberEntity(name = name, phoneNumber = phone, isActive = active,
                        rotationOrder = order, firebaseSyncId = fbKey)
                )
            } else {
                memberDao.updateMember(
                    existing.copy(name = name, phoneNumber = phone,
                        isActive = active, rotationOrder = order)
                )
            }
        }

        // Payments
        snapshot.child("payments").children.forEach { paySnap ->
            val fbKey     = paySnap.key ?: return@forEach
            val qty       = paySnap.child("quantity").getValue(Long::class.java)?.toInt() ?: return@forEach
            val amount    = paySnap.child("amount").getValue(Double::class.java) ?: return@forEach
            val payerName = paySnap.child("paidByNameSnapshot").getValue(String::class.java) ?: ""
            val date      = paySnap.child("purchaseDate").getValue(Long::class.java) ?: return@forEach
            val notes     = paySnap.child("notes").getValue(String::class.java)
            val vendor    = paySnap.child("vendorName").getValue(String::class.java)
            val isJoint   = paySnap.child("isJointPayment").getValue(Boolean::class.java) ?: false

            val allPayments = paymentDao.observeAllPayments().first()
            val existing = allPayments.firstOrNull { it.firebaseSyncId == fbKey }
            if (existing == null) {
                paymentDao.insertPayment(
                    PaymentEntity(
                        quantity           = qty,
                        amount             = amount,
                        paidByMemberId     = null,
                        paidByNameSnapshot = payerName,
                        purchaseDate       = date,
                        notes              = notes,
                        vendorName         = vendor,
                        isJointPayment     = isJoint,
                        firebaseSyncId     = fbKey
                    )
                )
            }
        }
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    private fun touchMeta(roomId: String) {
        db.reference.child("rooms/$roomId/meta/lastUpdatedAt")
            .setValue(System.currentTimeMillis())
    }

    private fun memberToMap(m: MemberEntity): Map<String, Any?> = mapOf(
        "name"          to m.name,
        "phoneNumber"   to m.phoneNumber,
        "isActive"      to m.isActive,
        "rotationOrder" to m.rotationOrder,
        "createdAt"     to m.createdAt
    )

    private fun paymentToMap(p: PaymentEntity): Map<String, Any?> = mapOf(
        "quantity"           to p.quantity,
        "amount"             to p.amount,
        "paidByNameSnapshot" to p.paidByNameSnapshot,
        "purchaseDate"       to p.purchaseDate,
        "notes"              to p.notes,
        "vendorName"         to p.vendorName,
        "isJointPayment"     to p.isJointPayment,
        "createdAt"          to p.createdAt
    )
}
