package com.ww.openboxcompanion

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persists the server list (including panel passwords) in encrypted
 * shared preferences backed by the Android Keystore. If encrypted
 * storage cannot be created on the device, it falls back to plain
 * private preferences and reports [secureStorageFailed] so the UI
 * can warn the user. Nothing is ever backed up (see the manifest)
 * or sent anywhere except the user's own panel.
 */
class ServerStore private constructor(context: Context) {

    companion object {
        private const val KEY_SERVERS = "servers_json"
        private const val KEY_SELECTED_ID = "selected_server_id"

        @Volatile
        private var instance: ServerStore? = null

        fun getInstance(context: Context): ServerStore {
            return instance ?: synchronized(this) {
                instance ?: ServerStore(context.applicationContext).also { instance = it }
            }
        }

        fun newId(): String = UUID.randomUUID().toString()
    }

    var secureStorageFailed: Boolean = false
        private set

    private val prefs: SharedPreferences

    init {
        var created: SharedPreferences? = null
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            created = EncryptedSharedPreferences.create(
                context,
                "panel_companion_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            secureStorageFailed = true
            created = null
        }
        prefs = created
            ?: context.getSharedPreferences("panel_companion_fallback", Context.MODE_PRIVATE)
    }

    fun isSecureStorage(): Boolean = !secureStorageFailed

    fun getServers(): List<Server> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        val result = mutableListOf<Server>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val host = obj.optString("host")
                if (id.isEmpty() || host.isEmpty()) continue
                result.add(
                    Server(
                        id = id,
                        name = obj.optString("name").ifEmpty { host },
                        scheme = obj.optString("scheme").ifEmpty { "http" },
                        host = host,
                        port = obj.optInt("port", 3036),
                        password = obj.optString("password")
                    )
                )
            }
        } catch (e: Exception) {
            return result
        }
        return result
    }

    fun getServer(id: String): Server? = getServers().firstOrNull { it.id == id }

    fun saveServer(server: Server) {
        val servers = getServers().toMutableList()
        val index = servers.indexOfFirst { it.id == server.id }
        if (index >= 0) {
            servers[index] = server
        } else {
            servers.add(server)
        }
        persist(servers)
        if (getSelectedServerId().isNullOrEmpty()) {
            setSelectedServerId(server.id)
        }
    }

    fun deleteServer(id: String) {
        val servers = getServers().filter { it.id != id }
        persist(servers)
        if (getSelectedServerId() == id) {
            val next = servers.firstOrNull()
            if (next != null) {
                setSelectedServerId(next.id)
            } else {
                prefs.edit().remove(KEY_SELECTED_ID).apply()
            }
        }
    }

    fun getSelectedServerId(): String? = prefs.getString(KEY_SELECTED_ID, null)

    fun setSelectedServerId(id: String) {
        prefs.edit().putString(KEY_SELECTED_ID, id).apply()
    }

    fun getSelectedServer(): Server? {
        val servers = getServers()
        if (servers.isEmpty()) return null
        val selectedId = getSelectedServerId()
        return servers.firstOrNull { it.id == selectedId } ?: servers.first()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun persist(servers: List<Server>) {
        val array = JSONArray()
        for (server in servers) {
            val obj = JSONObject()
            obj.put("id", server.id)
            obj.put("name", server.name)
            obj.put("scheme", server.scheme)
            obj.put("host", server.host)
            obj.put("port", server.port)
            obj.put("password", server.password)
            array.put(obj)
        }
        prefs.edit().putString(KEY_SERVERS, array.toString()).apply()
    }
}
