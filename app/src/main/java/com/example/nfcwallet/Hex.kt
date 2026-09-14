package com.example.nfcwallet

object Hex {
    private val HEX = "0123456789ABCDEF".toCharArray()

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    fun decode(s: String): ByteArray {
        val clean = s.replace(" ", "").replace(":", "").uppercase()
        require(clean.length % 2 == 0) { "Odd-length hex string" }
        val out = ByteArray(clean.length / 2)
        var i = 0
        while (i < clean.length) {
            out[i / 2] = ((Character.digit(clean[i], 16) shl 4) +
                    Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }
}
