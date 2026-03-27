package com.awesmoe.spennotes

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ServerProfile(
    val name: String,
    val localIp: String,
    val tailscaleIp: String,
    val port: Int = 5000
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("local_ip", localIp)
        put("tailscale_ip", tailscaleIp)
        put("port", port)
    }

    companion object {
        fun fromJson(obj: JSONObject) = ServerProfile(
            name = obj.optString("name", ""),
            localIp = obj.optString("local_ip", ""),
            tailscaleIp = obj.optString("tailscale_ip", ""),
            port = obj.optInt("port", 5000)
        )

        const val PREFS_KEY_PROFILES = "server_profiles"
        const val PREFS_KEY_ACTIVE = "active_server"

        fun loadAll(prefs: SharedPreferences): MutableList<ServerProfile> {
            val json = prefs.getString(PREFS_KEY_PROFILES, null) ?: return mutableListOf()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }.toMutableList()
            } catch (_: Exception) {
                mutableListOf()
            }
        }

        fun saveAll(prefs: SharedPreferences, profiles: List<ServerProfile>) {
            val arr = JSONArray()
            profiles.forEach { arr.put(it.toJson()) }
            prefs.edit().putString(PREFS_KEY_PROFILES, arr.toString()).apply()
        }

        fun getActive(prefs: SharedPreferences): Int =
            prefs.getInt(PREFS_KEY_ACTIVE, 0)

        fun setActive(prefs: SharedPreferences, index: Int) =
            prefs.edit().putInt(PREFS_KEY_ACTIVE, index).apply()
    }
}
