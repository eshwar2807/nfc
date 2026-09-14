package com.example.nfcwallet

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.io.ByteArrayOutputStream

/**
 * Host Card Emulation service.
 *
 * Emulates an ISO-DEP (ISO 14443-4, "Type 4") card. Two modes:
 *
 *  1. NDEF Type-4 tag emulation. If the active profile carries NDEF text,
 *     the phone answers the standard NDEF app / CC file / NDEF file APDU
 *     sequence, so any NFC reader that reads NDEF tags will read that text
 *     from the phone. This is the mode that works on a stock Pixel.
 *
 *  2. Captured-APDU replay. If the profile stored request->response APDU
 *     pairs, they are replayed verbatim. Useful only when you captured the
 *     exact exchange your reader performs.
 *
 * It cannot spoof the physical card UID (impossible without root), so
 * UID-based access systems (most MIFARE Classic fobs) will not be fooled.
 */
class CardEmulationService : HostApduService() {

    private val store by lazy { ProfileStore(this) }

    // ISO 7816 status words.
    private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
    private val SW_FILE_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
    private val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00)

    // Standard NDEF Type-4 identifiers.
    private val NDEF_APP_AID = Hex.decode("D2760000850101")
    private val CC_FILE_ID = Hex.decode("E103")
    private val NDEF_FILE_ID = Hex.decode("E104")

    private var selectedFile: Int = FILE_NONE

    private var ccFile: ByteArray = byteArrayOf()
    private var ndefFile: ByteArray = byteArrayOf()

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val apdu = commandApdu ?: return SW_INS_NOT_SUPPORTED
        val profile = store.activeProfile() ?: return SW_FILE_NOT_FOUND

        // Mode 2: exact replay if a captured response exists for this command.
        profile.apduResponses[Hex.encode(apdu)]?.let { return Hex.decode(it) }

        // Mode 1: NDEF Type-4 emulation from stored text.
        val text = profile.ndefText
        if (text.isNullOrEmpty()) return SW_FILE_NOT_FOUND
        if (ndefFile.isEmpty()) buildFiles(text)

        return when {
            isSelectAid(apdu, NDEF_APP_AID) -> { selectedFile = FILE_NONE; SW_OK }
            isSelectFile(apdu, CC_FILE_ID) -> { selectedFile = FILE_CC; SW_OK }
            isSelectFile(apdu, NDEF_FILE_ID) -> { selectedFile = FILE_NDEF; SW_OK }
            isReadBinary(apdu) -> readBinary(apdu)
            else -> SW_INS_NOT_SUPPORTED
        }
    }

    override fun onDeactivated(reason: Int) {
        selectedFile = FILE_NONE
    }

    private fun buildFiles(text: String) {
        val ndefMessage = buildNdefMessage(text)
        // NDEF file = 2-byte length + message.
        val nlen = ndefMessage.size
        ndefFile = byteArrayOf((nlen ushr 8).toByte(), (nlen and 0xFF).toByte()) + ndefMessage

        // Capability Container: 15-byte CC describing one NDEF file (read-only).
        ccFile = byteArrayOf(
            0x00, 0x0F,             // CCLEN = 15
            0x20,                   // mapping version 2.0
            0x00, 0x3B,             // max R-APDU data size
            0x00, 0x34,             // max C-APDU data size
            0x04, 0x06,             // NDEF File Control TLV: T=04, L=06
            0xE1.toByte(), 0x04,    // NDEF file id E104
            0x00, 0xFF.toByte(),    // max NDEF file size = 255
            0x00,                   // read access granted
            0xFF.toByte()           // write access denied
        )
    }

    /** Text -> a single NDEF message (URI record for http(s), else Text record). */
    private fun buildNdefMessage(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        val isUri = text.startsWith("http://") || text.startsWith("https://")
        if (isUri) {
            val prefix = if (text.startsWith("https://")) 0x04 else 0x03
            val body = text.removePrefix("https://").removePrefix("http://")
                .toByteArray(Charsets.UTF_8)
            val payloadLen = body.size + 1
            out.write(0xD1)             // MB+ME+SR, TNF=well known
            out.write(0x01)             // type length
            out.write(payloadLen)       // payload length
            out.write('U'.code)         // type = 'U' (URI)
            out.write(prefix)           // URI prefix code
            out.write(body)
        } else {
            val lang = "en".toByteArray(Charsets.US_ASCII)
            val body = text.toByteArray(Charsets.UTF_8)
            val payloadLen = 1 + lang.size + body.size
            out.write(0xD1)
            out.write(0x01)
            out.write(payloadLen)
            out.write('T'.code)         // type = 'T' (Text)
            out.write(lang.size)        // status byte = language length, UTF-8
            out.write(lang)
            out.write(body)
        }
        return out.toByteArray()
    }

    private fun isSelectAid(apdu: ByteArray, aid: ByteArray): Boolean {
        // 00 A4 04 00 Lc <aid> [Le]
        if (apdu.size < 5 + aid.size) return false
        if (apdu[0].toInt() and 0xFF != 0x00) return false
        if (apdu[1].toInt() and 0xFF != 0xA4) return false
        if (apdu[2].toInt() and 0xFF != 0x04) return false
        val lc = apdu[4].toInt() and 0xFF
        if (lc != aid.size) return false
        for (i in aid.indices) if (apdu[5 + i] != aid[i]) return false
        return true
    }

    private fun isSelectFile(apdu: ByteArray, fileId: ByteArray): Boolean {
        // 00 A4 00 0C 02 <fileId>
        if (apdu.size < 7) return false
        if (apdu[0].toInt() and 0xFF != 0x00) return false
        if (apdu[1].toInt() and 0xFF != 0xA4) return false
        if (apdu[2].toInt() and 0xFF != 0x00) return false
        if ((apdu[4].toInt() and 0xFF) != 0x02) return false
        return apdu[5] == fileId[0] && apdu[6] == fileId[1]
    }

    private fun isReadBinary(apdu: ByteArray): Boolean =
        apdu.size >= 5 &&
            (apdu[0].toInt() and 0xFF) == 0x00 &&
            (apdu[1].toInt() and 0xFF) == 0xB0

    private fun readBinary(apdu: ByteArray): ByteArray {
        val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)
        val le = apdu[4].toInt() and 0xFF
        val file = when (selectedFile) {
            FILE_CC -> ccFile
            FILE_NDEF -> ndefFile
            else -> return SW_FILE_NOT_FOUND
        }
        if (offset >= file.size) return SW_FILE_NOT_FOUND
        val end = minOf(offset + le, file.size)
        return file.copyOfRange(offset, end) + SW_OK
    }

    companion object {
        private const val FILE_NONE = 0
        private const val FILE_CC = 1
        private const val FILE_NDEF = 2
    }
}
