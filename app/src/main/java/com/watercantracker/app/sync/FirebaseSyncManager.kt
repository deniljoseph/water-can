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
    val errorCode: SyncErrorCode = SyncErrorCode.NONE,
    val connectedDevices: Int = 0
)

enum class SyncErrorCode {
    NONE, PLACEHOLDER_JSON, AUTH_TIMEOUT, DB_TIMEOUT, ROOM_NOT_FOUND, UNKNOWN
}

@Singleton
class FirebaseSyncManager @Inject constructor(
    private val memberDao: MemberDao,
    private val paymentDao: PaymentDao,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db    = FirebaseDatabase.getInstance(
        "https://water-can-tracker-a5033-default-rtdb.asia-southeast1.firebasedatabase.app"
    )
    private val auth  = FirebaseAuth.getInstance()

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState

    private var activeListener: ValueEventListener? = null
    private var activeRoomRef: com.google.firebase.database.DatabaseReference? = null

    // ── Auth ──────────────────────────────────────────────────────────────────

    private suspend fun ensureAuthenticated() {
        val projectId = com.google.firebase.FirebaseApp.getInstance().options.projectId ?: ""
        if (projectId.isBlank() || projectId == "YOUR_PROJECT_ID") {
            throw SyncException(SyncErrorCode.PLACEHOLDER_JSON, "Firebase is not configured yet.")
        }
        if (auth.currentUser != null) return
        try {
            withTimeout(30_000L) { auth.signInAnonymously().await() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw SyncException(SyncErrorCode.AUTH_TIMEOUT, "Authentication timed out after 30 s.")
        } catch (e: Exception) {
            throw SyncException(SyncErrorCode.UNKNOWN, "Auth failed: ${e.message}")
        }
    }

    // ── Room management ───────────────────────────────────────────────────────

    suspend fun createRoom(): String {
        _syncState.update { it.copy(status = SyncStatus.SYNCING, error = null, errorCode = SyncErrorCode.NONE) }
        return try {
            ensureAuthenticated()
            val roomRef = db.reference.child("rooms").push()
            val roomId  = roomRef.key ?: throw SyncException(SyncErrorCode.UNKNOWN, "Failed to allocate room key")

            try {
                withTimeout(30_000L) {
                    roomRef.child("meta").setValue(mapOf(
                        "masterDeviceId" to (auth.currentUser?.uid ?: "unknown"),
                        "createdAt"      to System.currentTimeMillis()
                    )).await()
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                throw SyncException(SyncErrorCode.DB_TIMEOUT,
                    "Database write timed out after 30 s. Check Realtime Database Rules.")
            }

            pushAllToFirebase(roomId)
            settingsRepository.updateFirebaseRoom(roomId, isMaster = true)
            _syncState.update {
                it.copy(status = SyncStatus.SUCCESS, roomId = roomId, isMaster = true,
                    lastSyncAt = System.currentTimeMillis(), error = null, errorCode = SyncErrorCode.NONE)
            }
            startListening(roomId, isMaster = true)
            roomId
        } catch (e: SyncException) {
            _syncState.update { it.copy(status = SyncStatus.ERROR, error = e.message, errorCode = e.code) }
            throw e
        } catch (e: Exception) {
            _syncState.update { it.copy(status = SyncStatus.ERROR, error = "Unexpected: ${e.message}", errorCode = SyncErrorCode.UNKNOWN) }
            throw e
        }
    }

    suspend fun joinRoom(roomId: String) {
        _syncState.update { it.copy(status = SyncStatus.SYNCING, error = null, errorCode = SyncErrorCode.NONE) }
        try {
            ensureAuthenticated()
            val snapshot = try {
                withTimeout(30_000L) { db.reference.child("rooms").child(roomId).get().await() }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                throw SyncException(SyncErrorCode.DB_TIMEOUT, "Database read timed out after 30 s. Check Realtime Database Rules.")
            }
            if (!snapshot.exists()) throw SyncException(SyncErrorCode.ROOM_NOT_FOUND, "Room \"$roomId\" not found.")

            pullFromFirebase(snapshot)
            settingsRepository.updateFirebaseRoom(roomId, isMaster = false)
            _syncState.update {
                it.copy(status = SyncStatus.SUCCESS, roomId = roomId, isMaster = false,
                    lastSyncAt = System.currentTimeMillis(), error = null, errorCode = SyncErrorCode.NONE)
            }
            startListening(roomId, isMaster = false)
        } catch (e: SyncException) {
            _syncState.update { it.copy(status = SyncStatus.ERROR, error = e.message, errorCode = e.code) }
        } catch (e: Exception) {
            _syncState.update { it.copy(status = SyncStatus.ERROR, error = "Unexpected: ${e.message}", errorCode = SyncErrorCode.UNKNOWN) }
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
                withTimeout(30_000L) { ref.setValue(paymentToMap(payment)).await() }
                ref.key?.let { key ->
                    if (payment.firebaseSyncId == null)
                        paymentDao.updatePayment(payment.copy(firebaseSyncId = key))
                }
                touchMeta(roomId)
            } catch (_: Exception) {}
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
                withTimeout(30_000L) { ref.setValue(memberToMap(member)).await() }
                ref.key?.let { key ->
                    if (member.firebaseSyncId == null)
                        memberDao.updateMember(member.copy(firebaseSyncId = key))
                }
                touchMeta(roomId)
            } catch (_: Exception) {}
        }
    }

    fun deleteMember(roomId: String, firebaseSyncId: String) {
        if (!_syncState.value.isMaster) return
        scope.launch {
            try {
                withTimeout(30_000L) {
                    db.reference.child("rooms/$roomId/members/$firebaseSyncId").removeValue().await()
                }
                touchMeta(roomId)
            } catch (_: Exception) {}
        }
    }

    fun deletePayment(roomId: String, firebaseSyncId: String) {
        if (!_syncState.value.isMaster) return
        scope.launch {
            try {
                withTimeout(30_000L) {
                    db.reference.child("rooms/$roomId/payments/$firebaseSyncId").removeValue().await()
                }
                touchMeta(roomId)
            } catch (_: Exception) {}
        }
    }

    // ── Listener ──────────────────────────────────────────────────────────────

    fun startListening(roomId: String, isMaster: Boolean) {
        stopListening()
        val roomRef = db.reference.child("rooms").child(roomId)
        activeRoomRef = roomRef
        activeListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isMaster) {
                    scope.launch {
                        try { pullFromFirebase(snapshot) } catch (_: Exception) {}
                    }
                }
                _syncState.update {
                    it.copy(
                        status     = SyncStatus.SUCCESS,
                        lastSyncAt = System.currentTimeMillis(),
                        error      = null,
                        errorCode  = SyncErrorCode.NONE,
                        connectedDevices = snapshot.child("meta/connectedDevices")
                            .getValue(Long::class.java)?.toInt() ?: 1
                    )
                }
            }
            override fun onCancelled(error: DatabaseError) {
                _syncState.update {
                    it.copy(status = SyncStatus.ERROR, error = "Listener: ${error.message}",
                        errorCode = SyncErrorCode.UNKNOWN)
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

    // ── Data sync ─────────────────────────────────────────────────────────────

    private suspend fun pushAllToFirebase(roomId: String) {
        memberDao.getAllMembers().forEach { m ->
            try {
                val ref = db.reference.child("rooms/$roomId/members").push()
                withTimeout(30_000L) { ref.setValue(memberToMap(m)).await() }
                ref.key?.let { memberDao.updateMember(m.copy(firebaseSyncId = it)) }
            } catch (_: Exception) {}
        }
        paymentDao.observeAllPayments().first().forEach { p ->
            try {
                val ref = db.reference.child("rooms/$roomId/payments").push()
                withTimeout(30_000L) { ref.setValue(paymentToMap(p)).await() }
                ref.key?.let { paymentDao.updatePayment(p.copy(firebaseSyncId = it)) }
            } catch (_: Exception) {}
        }
    }

    /**
     * Pull members FIRST, build a name→localId map, then pull payments and
     * resolve paidByMemberId using that map so payments link to the correct
     * local member on secondary devices.
     */
    private suspend fun pullFromFirebase(snapshot: DataSnapshot) {

        // ── Step 1: sync members ──────────────────────────────────────────────
        snapshot.child("members").children.forEach { s ->
            val fbKey  = s.key ?: return@forEach
            val name   = s.child("name").getValue(String::class.java) ?: return@forEach
            val phone  = s.child("phoneNumber").getValue(String::class.java)
            val active = s.child("isActive").getValue(Boolean::class.java) ?: true
            val order  = s.child("rotationOrder").getValue(Long::class.java)?.toInt() ?: 0

            val existing = memberDao.getAllMembers().firstOrNull { it.firebaseSyncId == fbKey }
            if (existing == null) {
                memberDao.insertMember(
                    MemberEntity(
                        name          = name,
                        phoneNumber   = phone,
                        isActive      = active,
                        rotationOrder = order,
                        firebaseSyncId = fbKey
                    )
                )
            } else {
                // Update name/phone/active/order but keep local ID and settings
                memberDao.updateMember(
                    existing.copy(
                        name          = name,
                        phoneNumber   = phone,
                        isActive      = active,
                        rotationOrder = order
                    )
                )
            }
        }

        // ── Step 2: build fbKey→localId + name→localId lookup maps ───────────
        // Re-read after insert so newly created members are included
        val allLocalMembers = memberDao.getAllMembers()
        val fbKeyToLocalId  = allLocalMembers
            .filter { it.firebaseSyncId != null }
            .associate { it.firebaseSyncId!! to it.id }
        val nameToLocalId   = allLocalMembers.associate { it.name to it.id }

        // ── Step 3: sync payments ─────────────────────────────────────────────
        snapshot.child("payments").children.forEach { s ->
            val fbKey     = s.key ?: return@forEach
            val qty       = s.child("quantity").getValue(Long::class.java)?.toInt() ?: return@forEach
            val amount    = s.child("amount").getValue(Double::class.java) ?: return@forEach
            val payerName = s.child("paidByNameSnapshot").getValue(String::class.java) ?: ""
            val date      = s.child("purchaseDate").getValue(Long::class.java) ?: return@forEach
            val notes     = s.child("notes").getValue(String::class.java)
            val vendor    = s.child("vendorName").getValue(String::class.java)
            val joint     = s.child("isJointPayment").getValue(Boolean::class.java) ?: false
            // masterMemberId is the original local ID on the master device — we use
            // it to look up by Firebase sync key first, then fall back to name match
            val masterFbMemberKey = s.child("payerFbKey").getValue(String::class.java)

            // Resolve to this device's local member ID
            val localMemberId = masterFbMemberKey?.let { fbKeyToLocalId[it] }
                ?: nameToLocalId[payerName]

            val existing = paymentDao.observeAllPayments().first()
                .firstOrNull { it.firebaseSyncId == fbKey }

            if (existing == null) {
                paymentDao.insertPayment(
                    PaymentEntity(
                        quantity           = qty,
                        amount             = amount,
                        paidByMemberId     = localMemberId,   // ← now resolved!
                        paidByNameSnapshot = payerName,
                        purchaseDate       = date,
                        notes              = notes,
                        vendorName         = vendor,
                        isJointPayment     = joint,
                        firebaseSyncId     = fbKey
                    )
                )
            } else if (existing.paidByMemberId == null && localMemberId != null) {
                // Fix previously synced payments that had null memberId
                paymentDao.updatePayment(existing.copy(paidByMemberId = localMemberId))
            }
        }
    }

    private fun touchMeta(roomId: String) {
        db.reference.child("rooms/$roomId/meta/lastUpdatedAt").setValue(System.currentTimeMillis())
    }

    private fun memberToMap(m: MemberEntity): Map<String, Any?> = mapOf(
        "name"          to m.name,
        "phoneNumber"   to m.phoneNumber,
        "isActive"      to m.isActive,
        "rotationOrder" to m.rotationOrder,
        "createdAt"     to m.createdAt
        // Note: we do NOT push localId — each device has its own Room auto-IDs.
        // We match by firebaseSyncId (the Firebase push key) and name as fallback.
    )

    private fun paymentToMap(p: PaymentEntity): Map<String, Any?> {
        // Also push the payer's Firebase member key so secondary devices can
        // resolve paidByMemberId even if the member name isn't unique
        val payerFbKey = p.paidByMemberId?.let { memberId ->
            // This is a best-effort lookup — fire and forget
            var fbKey: String? = null
            try {
                kotlinx.coroutines.runBlocking {
                    fbKey = memberDao.getMemberById(memberId)?.firebaseSyncId
                }
            } catch (_: Exception) {}
            fbKey
        }
        return mapOf(
            "quantity"           to p.quantity,
            "amount"             to p.amount,
            "paidByNameSnapshot" to p.paidByNameSnapshot,
            "payerFbKey"         to payerFbKey,
            "purchaseDate"       to p.purchaseDate,
            "notes"              to p.notes,
            "vendorName"         to p.vendorName,
            "isJointPayment"     to p.isJointPayment,
            "createdAt"          to p.createdAt
        )
    }
}

class SyncException(val code: SyncErrorCode, message: String) : Exception(message)
