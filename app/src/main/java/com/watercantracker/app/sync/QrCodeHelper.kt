package com.watercantracker.app.sync

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QrCodeHelper @Inject constructor() {

    /**
     * Generates a QR code bitmap encoding the Firebase room ID.
     * The joining device scans this, extracts the room ID, and calls FirebaseSyncManager.joinRoom().
     *
     * QR payload format: "watercan://sync/{roomId}"
     */
    fun generateRoomQr(roomId: String, sizePx: Int = 512): Bitmap {
        val payload = "watercan://sync/$roomId"
        val hints   = mapOf(EncodeHintType.MARGIN to 2)
        val writer  = QRCodeWriter()
        val matrix  = writer.encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    /** Extract room ID from a scanned QR payload */
    fun extractRoomId(qrPayload: String): String? {
        val prefix = "watercan://sync/"
        return if (qrPayload.startsWith(prefix)) qrPayload.removePrefix(prefix).trim() else null
    }
}
