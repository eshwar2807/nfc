package com.example.nfcwallet

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.UUID

class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private lateinit var store: ProfileStore
    private var nfcAdapter: NfcAdapter? = null

    private lateinit var statusText: TextView
    private lateinit var scanResult: TextView
    private lateinit var saveRow: LinearLayout
    private lateinit var nameInput: EditText
    private lateinit var saveButton: Button
    private lateinit var emptyText: TextView
    private lateinit var profileList: RecyclerView
    private lateinit var adapter: ProfileAdapter

    /** Card most recently scanned, awaiting a name + save. */
    private var pendingScan: CardProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = ProfileStore(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        statusText = findViewById(R.id.statusText)
        scanResult = findViewById(R.id.scanResult)
        saveRow = findViewById(R.id.saveRow)
        nameInput = findViewById(R.id.nameInput)
        saveButton = findViewById(R.id.saveButton)
        emptyText = findViewById(R.id.emptyText)
        profileList = findViewById(R.id.profileList)

        adapter = ProfileAdapter(
            store.all(), store.activeProfileId,
            onActivate = { activate(it) },
            onDelete = { delete(it) }
        )
        profileList.layoutManager = LinearLayoutManager(this)
        profileList.adapter = adapter

        saveButton.setOnClickListener { savePendingScan() }

        if (nfcAdapter == null) {
            statusText.text = "This device has no NFC hardware."
        }
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        // Reader mode across all card families; keep the platform NDEF check on
        // so NDEF data is cached and readable in onTagDiscovered.
        nfcAdapter?.enableReaderMode(
            this, this,
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    /** Runs on a binder thread when a tag enters the field. */
    override fun onTagDiscovered(tag: Tag) {
        val uid = Hex.encode(tag.id)
        val techList = tag.techList.toList()

        var atqa: String? = null
        var sak: String? = null
        NfcA.get(tag)?.let { nfcA ->
            atqa = Hex.encode(nfcA.atqa)
            sak = String.format("%02X", nfcA.sak.toInt() and 0xFF)
        }

        var ndefText: String? = null
        Ndef.get(tag)?.let { ndef ->
            val msg = ndef.cachedNdefMessage ?: runCatching {
                ndef.connect(); ndef.ndefMessage
            }.getOrNull().also { runCatching { ndef.close() } }
            ndefText = msg?.records?.firstOrNull()?.let { decodeNdefRecord(it.payload, it.type) }
        }

        val profile = CardProfile(
            id = UUID.randomUUID().toString(),
            name = "",
            uidHex = uid,
            techList = techList,
            atqa = atqa,
            sak = sak,
            ndefText = ndefText
        )
        pendingScan = profile

        runOnUiThread { showScan(profile) }
    }

    private fun decodeNdefRecord(payload: ByteArray, type: ByteArray): String? {
        if (payload.isEmpty()) return null
        val typeStr = String(type, Charsets.US_ASCII)
        return when (typeStr) {
            "T" -> {
                val langLen = payload[0].toInt() and 0x3F
                String(payload, 1 + langLen, payload.size - 1 - langLen, Charsets.UTF_8)
            }
            "U" -> {
                val prefixes = arrayOf(
                    "", "http://www.", "https://www.", "http://", "https://",
                    "tel:", "mailto:"
                )
                val code = payload[0].toInt() and 0xFF
                val prefix = prefixes.getOrElse(code) { "" }
                prefix + String(payload, 1, payload.size - 1, Charsets.UTF_8)
            }
            else -> String(payload, Charsets.UTF_8)
        }
    }

    private fun showScan(p: CardProfile) {
        val tech = p.techList.joinToString(", ") { it.substringAfterLast('.') }
        val sb = StringBuilder()
        sb.append("UID:  ${p.uidHex}\n")
        sb.append("Tech: $tech\n")
        p.atqa?.let { sb.append("ATQA: $it   SAK: ${p.sak}\n") }
        p.ndefText?.let { sb.append("NDEF: $it\n") }
        sb.append("\n")
        sb.append(
            if (p.emulationSupported())
                "This card speaks ISO-DEP. The phone can emulate it via HCE."
            else
                "This is a UID-based card. A stock phone cannot clone its UID, " +
                    "so most readers for this card type will not accept the phone. " +
                    "You can still save it for reference."
        )
        scanResult.text = sb.toString()
        scanResult.visibility = TextView.VISIBLE
        saveRow.visibility = LinearLayout.VISIBLE
        nameInput.setText("")
        statusText.text = "Card detected. Name it and tap Save."
        Toast.makeText(this, "Card detected", Toast.LENGTH_SHORT).show()
    }

    private fun savePendingScan() {
        val scan = pendingScan ?: return
        val name = nameInput.text.toString().trim().ifEmpty { "Card ${scan.uidHex.take(8)}" }
        val saved = scan.copy(name = name)
        store.save(saved)
        if (store.activeProfileId == null && saved.emulationSupported()) {
            store.activeProfileId = saved.id
        }
        pendingScan = null
        saveRow.visibility = LinearLayout.GONE
        scanResult.visibility = TextView.GONE
        statusText.text = "Saved \"$name\". Scan another card or use a saved one below."
        refreshList()
    }

    private fun activate(p: CardProfile) {
        store.activeProfileId = p.id
        if (!p.emulationSupported()) {
            Toast.makeText(
                this,
                "Marked active, but this UID-based card can't be emulated on a stock phone.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "\"${p.name}\" is now active for tap-to-use.", Toast.LENGTH_SHORT).show()
        }
        refreshList()
    }

    private fun delete(p: CardProfile) {
        store.delete(p.id)
        if (store.activeProfileId == p.id) store.activeProfileId = null
        refreshList()
    }

    private fun refreshList() {
        val all = store.all()
        emptyText.visibility = if (all.isEmpty()) TextView.VISIBLE else TextView.GONE
        adapter.update(all, store.activeProfileId)
    }
}
