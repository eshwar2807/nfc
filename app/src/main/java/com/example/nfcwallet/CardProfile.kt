package com.example.nfcwallet

import org.json.JSONArray
import org.json.JSONObject

/**
 * A captured NFC card. Depending on the physical card, only some of these
 * fields can actually be re-used by the phone (see [emulationSupported]).
 */
data class CardProfile(
    val id: String,
    val name: String,
    val uidHex: String,
    val techList: List<String>,
    val atqa: String?,
    val sak: String?,
    val ndefText: String?,
    /** Optional APDU request->response map for ISO-DEP / Type-4 emulation. */
    val apduResponses: Map<String, String> = emptyMap(),
    val capturedAt: Long = System.currentTimeMillis()
) {
    /**
     * Stock (non-rooted) Android can only emulate ISO-DEP (ISO 14443-4,
     * "Type 4") cards through Host Card Emulation, and it cannot spoof the
     * physical UID. So a card is realistically emulatable only when it speaks
     * ISO-DEP and its reader does not authenticate on the UID.
     */
    fun emulationSupported(): Boolean =
        techList.any { it.endsWith("IsoDep") }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("uidHex", uidHex)
        put("techList", JSONArray(techList))
        put("atqa", atqa)
        put("sak", sak)
        put("ndefText", ndefText)
        put("capturedAt", capturedAt)
        val apdu = JSONObject()
        apduResponses.forEach { (k, v) -> apdu.put(k, v) }
        put("apduResponses", apdu)
    }

    companion object {
        fun fromJson(o: JSONObject): CardProfile {
            val tech = mutableListOf<String>()
            o.optJSONArray("techList")?.let { arr ->
                for (i in 0 until arr.length()) tech.add(arr.getString(i))
            }
            val apdu = mutableMapOf<String, String>()
            o.optJSONObject("apduResponses")?.let { j ->
                val keys = j.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    apdu[k] = j.getString(k)
                }
            }
            return CardProfile(
                id = o.getString("id"),
                name = o.getString("name"),
                uidHex = o.optString("uidHex", ""),
                techList = tech,
                atqa = if (o.isNull("atqa")) null else o.optString("atqa"),
                sak = if (o.isNull("sak")) null else o.optString("sak"),
                ndefText = if (o.isNull("ndefText")) null else o.optString("ndefText"),
                apduResponses = apdu,
                capturedAt = o.optLong("capturedAt", 0L)
            )
        }
    }
}
