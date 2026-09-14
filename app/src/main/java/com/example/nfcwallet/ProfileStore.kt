package com.example.nfcwallet

import android.content.Context
import org.json.JSONArray

/** Persists captured cards in SharedPreferences as JSON. */
class ProfileStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("nfc_wallet", Context.MODE_PRIVATE)

    fun all(): List<CardProfile> {
        val raw = prefs.getString(KEY_PROFILES, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = ArrayList<CardProfile>(arr.length())
        for (i in 0 until arr.length()) {
            out.add(CardProfile.fromJson(arr.getJSONObject(i)))
        }
        return out.sortedByDescending { it.capturedAt }
    }

    fun save(profile: CardProfile) {
        val current = all().filter { it.id != profile.id }.toMutableList()
        current.add(profile)
        persist(current)
    }

    fun delete(id: String) {
        persist(all().filter { it.id != id })
    }

    private fun persist(profiles: List<CardProfile>) {
        val arr = JSONArray()
        profiles.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    var activeProfileId: String?
        get() = prefs.getString(KEY_ACTIVE, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE, value).apply()

    fun activeProfile(): CardProfile? =
        activeProfileId?.let { id -> all().firstOrNull { it.id == id } }

    companion object {
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE = "active_profile"
    }
}
