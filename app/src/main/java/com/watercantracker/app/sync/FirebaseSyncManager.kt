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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

/**
 * Firebase Realtime Database sync manager.
 *
 * Database structure:
 * /rooms/{roomId}/
 *   members/{firebaseSyncId}/ → MemberEntity fields
 *   payments/{firebaseSyncId}/ → PaymentEntity fields
 *   meta/
 *     masterDeviceId: String
 *     lastUpdatedAt: Long
 *     connectedDevices: Int
 *
 * The master device pushes all writes.
 * Non-master devices listen and pull changes into local Room DB.
 * Both directions work offline and sync when reconnected.
 */
@Singleton
class FirebaseSyncManager @Inject constructor(
    private val memberDao: MemberDao,
    private val paymentDao: PaymentDao,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState

    private var activeListener: ValueEventListener? = null
    private var activeRoomId: String? = null

    /** Sign in anonymously to get a stable device identity */
    suspend fun ensureAuthenticated() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    /** Master device: create a new room and return its ID */
    suspend fun createRoom(): String {
        ensureAuthenticated()
        val roomRef = db.reference.child("rooms").push()
        val roomId  = roomRef.key ?: throw Exception("Failed to create room")

        roomRef.child("meta").child("masterDeviceId").setValue(auth.currentUser?.uid)
        roomRef.child("meta").child("createdAt").setValue(System.currentTimeMillis())

        // Push existing local data to the new room
        pushAllToFirebase(roomId)

        settingsRepository.updateFirebaseRoom(roomId, isMaster = true)
        _syncState.value = SyncState(
            status  = SyncStatus.SUCCESS,
            roomId  = roomId,
            isMaster = true,
            lastSyncAt = System.currentTimeMillis()
        )
        startListening(roomId, isMaster = true)
        return roomId
    }

    /** Non-master device: join an existing room by its ID */
    suspend fun joinRoom(roomId: String) {
        ensureAuthenticated()
        _syncState.value = _syncState.value.copy(status = SyncStatus.SYNCING)
        try {
            val roomRef  = db.reference.child("rooms").child(roomId)
            val snapshot = roomRef.get().await()
            if (!snapshot.exists()) throw Exception("Room not found: $roomId")

            // Pull all data from Firebase into local Room DB
            pullFromFirebase(snapshot)
            settingsRepository.updateFirebaseRoom(roomId, isMaster = false)
            _syncState.value = SyncState(
                status    = SyncStatus.SUCCESS,
                roomId    = roomId,
                isMaster  = false,
                lastSyncAt = System.currentTimeMillis()
            )
            startListening(roomId, isMaster = false)
        } catch (e: Exception) {
            _syncState.value = _syncState.value.copy(status = SyncStatus.ERROR, error = e.message)
        }
    }

    /** Push a single payment to Firebase (called after insert/update on master) */
    fun pushPayment(roomId: String, payment: PaymentEntity) {
        if (!_syncState.value.isMaster) return
        val ref = if (payment.firebaseSyncId != null)
            db.reference.child("rooms").child(roomId).child("payments").child(payment.firebaseSyncId)
        else
            db.reference.child("rooms").child(roomId).child("payments").push()

        ref.setValue(paymentToMap(payment))
        ref.key?.let { fbKey ->
            scope.launch {
                paymentDao.updatePayment(payment.copy(firebaseSyncId = fbKey))
            }
        }
        touchMeta(roomId)
    }

    /** Push a single member to Firebase */
    fun pushMember(roomId: String, member: MemberEntity) {
        if (!_syncState.value.isMaster) return
        val ref = if (member.firebaseSyncId != null)
            db.reference.child("rooms").child(roomId).child("members").child(member.firebaseSyncId)
        else
            db.reference.child("rooms").child(roomId).child("members").push()

        ref.setValue(memberToMap(member))
        ref.key?.let { fbKey ->
            scope.launch { memberDao.updateMember(member.copy(firebaseSyncId = fbKey)) }
        }
        touchMeta(roomId)
    }

    /** Delete a payment from Firebase */
    fun deletePayment(roomId: String, firebaseSyncId: String) {
        if (!_syncState.value.isMaster) return
        db.reference.child("rooms").child(roomId).child("payments").child(firebaseSyncId).removeValue()
        touchMeta(roomId)
    }

    /** Start a realtime listener — syncs changes from Firebase → Room */
    fun startListening(roomId: String, isMaster: Boolean) {
        stopListening()
        activeRoomId = roomId
        val roomRef = db.reference.child("rooms").child(roomId)
        activeListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isMaster) {
                    scope.launch { pullFromFirebase(snapshot) }
                }
                val devices = snapshot.child("meta").child("connectedDevices")
                    .getValue(Int::class.java) ?: 1
                _syncState.value = _syncState.value.copy(
                    status = SyncStatus.SUCCESS,
                    lastSyncAt = System.currentTimeMillis(),
                    connectedDevices = devices
                )
            }
            override fun onCancelled(error: DatabaseError) {
                _syncState.value = _syncState.value.copy(
                    status = SyncStatus.ERROR, error = error.message
                )
            }
        }
        roomRef.addValueEventListener(activeListener!!)
    }

    fun stopListening() {
        activeRoomId?.let { rid ->
            activeListener?.let { listener ->
                db.reference.child("rooms").child(rid).removeEventListener(listener)
            }
        }
        activeListener = null
        activeRoomId  = null
    }

    fun disconnect() {
        stopListening()
        _syncState.value = SyncState(status = SyncStatus.DISABLED)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun pushAllToFirebase(roomId: String) {
        val members  = memberDao.getAllMembers()
        val payments = paymentDao.observeAllPayments().first()
        val roomRef  = db.reference.child("rooms").child(roomId)

        members.forEach { m ->
            val ref = roomRef.child("members").push()
            ref.setValue(memberToMap(m))
            ref.key?.let { memberDao.updateMember(m.copy(firebaseSyncId = it)) }
        }
        payments.forEach { p ->
            val ref = roomRef.child("payments").push()
            ref.setValue(paymentToMap(p))
            ref.key?.let { paymentDao.updatePayment(p.copy(firebaseSyncId = it)) }
        }
    }

    private suspend fun pullFromFirebase(snapshot: DataSnapshot) {
        // Pull members
        snapshot.child("members").children.forEach { memberSnap ->
            val fbKey = memberSnap.key ?: return@forEach
            val existing = memberDao.getAllMembers().firstOrNull { it.firebaseSyncId == fbKey }
            val name  = memberSnap.child("name").getValue(String::class.java) ?: return@forEach
            val phone = memberSnap.child("phoneNumber").getValue(String::class.java)
            val active = memberSnap.child("isActive").getValue(Boolean::class.java) ?: true
            val order  = memberSnap.child("rotationOrder").getValue(Int::class.java) ?: 0

            if (existing == null) {
                memberDao.insertMember(
                    MemberEntity(name = name, phoneNumber = phone, isActive = active,
                        rotationOrder = order, firebaseSyncId = fbKey)
                )
            } else {
                memberDao.updateMember(existing.copy(name = name, phoneNumber = phone,
                    isActive = active, rotationOrder = order))
            }
        }

        // Pull payments
        snapshot.child("payments").children.forEach { paySnap ->
            val fbKey    = paySnap.key ?: return@forEach
            val qty      = paySnap.child("quantity").getValue(Int::class.java) ?: return@forEach
            val amount   = paySnap.child("amount").getValue(Double::class.java) ?: return@forEach
            val payerName = paySnap.child("paidByNameSnapshot").getValue(String::class.java) ?: ""
            val date     = paySnap.child("purchaseDate").getValue(Long::class.java) ?: return@forEach
            val notes    = paySnap.child("notes").getValue(String::class.java)
            val vendor   = paySnap.child("vendorName").getValue(String::class.java)
            val isJoint  = paySnap.child("isJointPayment").getValue(Boolean::class.java) ?: false

            val existing = paymentDao.observeAllPayments().first()
                .firstOrNull { it.firebaseSyncId == fbKey }

            if (existing == null) {
                paymentDao.insertPayment(
                    PaymentEntity(quantity = qty, amount = amount,
                        paidByMemberId = null, paidByNameSnapshot = payerName,
                        purchaseDate = date, notes = notes, vendorName = vendor,
                        isJointPayment = isJoint, firebaseSyncId = fbKey)
                )
            }
        }
    }

    private fun touchMeta(roomId: String) {
        db.reference.child("rooms").child(roomId).child("meta")
            .child("lastUpdatedAt").setValue(System.currentTimeMillis())
    }

    private fun memberToMap(m: MemberEntity) = mapOf(
        "name"          to m.name,
        "phoneNumber"   to m.phoneNumber,
        "isActive"      to m.isActive,
        "rotationOrder" to m.rotationOrder,
        "createdAt"     to m.createdAt
    )

    private fun paymentToMap(p: PaymentEntity) = mapOf(
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
