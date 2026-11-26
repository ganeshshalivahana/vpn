package com.example.myvpn

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wireguard.config.Config
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.UUID

data class Server(
    val id: String,
    val name: String,
    val country: String, // ISO 3166 two-letter code, or "ZZ" for unknown
    val config: String
)

object Countries {
    fun name(code: String): String =
        if (code == "ZZ" || code.length != 2) "Other"
        else Locale("", code).getDisplayCountry(Locale.ENGLISH).ifBlank { code }

    fun flag(code: String): String {
        if (code.length != 2 || code == "ZZ") return "🌐"
        val up = code.uppercase(Locale.ROOT)
        return String(Character.toChars(0x1F1E6 + (up[0] - 'A'))) +
            String(Character.toChars(0x1F1E6 + (up[1] - 'A')))
    }

    /** "us-nyc-wg-001.conf", "NL-FREE#12.conf", "de_berlin.conf" -> US / NL / DE. */
    fun fromFileName(fileName: String): String {
        val m = Regex("^([A-Za-z]{2})[-_ .#]").find(fileName)
        return m?.groupValues?.get(1)?.uppercase(Locale.ROOT) ?: "ZZ"
    }
}

/** Stores imported WireGuard configs (which contain private keys) in encrypted preferences. */
class ServerStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "servers_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Keystore unavailable on some devices; fall back to app-private storage.
        context.getSharedPreferences("servers_plain", Context.MODE_PRIVATE)
    }

    fun load(): List<Server> = try {
        val arr = JSONArray(prefs.getString("servers", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Server(o.getString("id"), o.getString("name"), o.getString("country"), o.getString("config"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(list: List<Server>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name)
                .put("country", it.country).put("config", it.config))
        }
        prefs.edit().putString("servers", arr.toString()).apply()
    }

    companion object {
        /** Returns the new Server, or throws if the text is not a valid WireGuard config. */
        fun create(name: String, country: String, configText: String): Server {
            Config.parse(ByteArrayInputStream(configText.toByteArray())) // validates
            return Server(UUID.randomUUID().toString(), name.ifBlank { "Server" },
                country.uppercase(Locale.ROOT).ifBlank { "ZZ" }, configText)
        }
    }
}
