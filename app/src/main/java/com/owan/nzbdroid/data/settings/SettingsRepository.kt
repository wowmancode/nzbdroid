package com.owan.nzbdroid.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for all user-entered config: the NZBGet server, the list of
 * Newznab indexers, and the saved SMB share. Backed by EncryptedSharedPreferences so
 * passwords/API keys aren't sitting in plaintext on disk.
 *
 * Everything is exposed as a StateFlow so screens recompose automatically when settings
 * change, and everything is also written through to disk synchronously on every update
 * (this data is tiny — a handful of strings — so there's no need for DataStore's async
 * machinery here).
 */
class SettingsRepository private constructor(context: Context) {

    private val gson = Gson()

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nzbdroid_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _nzbGet = MutableStateFlow(loadNzbGet())
    val nzbGet: StateFlow<NzbGetConfig> = _nzbGet.asStateFlow()

    private val _indexers = MutableStateFlow(loadIndexers())
    val indexers: StateFlow<List<IndexerConfig>> = _indexers.asStateFlow()

    private val _smb = MutableStateFlow(loadSmb())
    val smb: StateFlow<SmbConfig> = _smb.asStateFlow()

    fun saveNzbGet(config: NzbGetConfig) {
        prefs.edit()
            .putString(KEY_NZBGET, gson.toJson(config))
            .apply()
        _nzbGet.value = config
    }

    fun saveIndexers(list: List<IndexerConfig>) {
        prefs.edit()
            .putString(KEY_INDEXERS, gson.toJson(list))
            .apply()
        _indexers.value = list
    }

    fun upsertIndexer(config: IndexerConfig) {
        val current = _indexers.value.toMutableList()
        val idx = current.indexOfFirst { it.id == config.id }
        if (idx >= 0) current[idx] = config else current.add(config)
        saveIndexers(current)
    }

    fun removeIndexer(id: String) {
        saveIndexers(_indexers.value.filterNot { it.id == id })
    }

    fun saveSmb(config: SmbConfig) {
        prefs.edit()
            .putString(KEY_SMB, gson.toJson(config))
            .apply()
        _smb.value = config
    }

    private fun loadNzbGet(): NzbGetConfig {
        val json = prefs.getString(KEY_NZBGET, null) ?: return NzbGetConfig()
        return runCatching { gson.fromJson(json, NzbGetConfig::class.java) }.getOrDefault(NzbGetConfig())
    }

    private fun loadIndexers(): List<IndexerConfig> {
        val json = prefs.getString(KEY_INDEXERS, null) ?: return emptyList()
        val type = object : TypeToken<List<IndexerConfig>>() {}.type
        return runCatching { gson.fromJson<List<IndexerConfig>>(json, type) }.getOrDefault(emptyList())
    }

    private fun loadSmb(): SmbConfig {
        val json = prefs.getString(KEY_SMB, null) ?: return SmbConfig()
        return runCatching { gson.fromJson(json, SmbConfig::class.java) }.getOrDefault(SmbConfig())
    }

    companion object {
        private const val KEY_NZBGET = "nzbget_config"
        private const val KEY_INDEXERS = "indexer_configs"
        private const val KEY_SMB = "smb_config"

        @Volatile private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
