package com.letstrack.app.domain.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.letstrack.app.di.AiDataStoreQualifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val ACTIVE_PROVIDER = stringPreferencesKey("active_ai_provider")
private fun apiKeyPrefKey(provider: AiProvider) = stringPreferencesKey("api_key_${provider.id}")

/**
 * BYOK, now for multiple providers -- a user can save a key for more than one (per "user can add
 * multiple, but will be using only one"), with [activeProvider] tracking which one actually gets
 * used when the chat screen sends a message. Plaintext in DataStore, same as every other local
 * preference in this app (theme, etc.) -- flagging that explicitly rather than implying it's
 * encrypted: a credential this sensitive would ideally sit behind
 * EncryptedSharedPreferences/Android Keystore, which isn't wired up yet.
 */
@Singleton
class AiSettingsRepository @Inject constructor(
    @AiDataStoreQualifier private val dataStore: DataStore<Preferences>
) {
    val activeProvider: Flow<AiProvider?> = dataStore.data.map { prefs ->
        prefs[ACTIVE_PROVIDER]?.let { id -> AiProvider.entries.find { it.id == id } }
    }

    /** Which providers currently have a saved key -- drives the "Saved"/"Active" state in the
     * Settings picker. */
    val savedProviderIds: Flow<Set<String>> = dataStore.data.map { prefs ->
        AiProvider.entries.filter { !prefs[apiKeyPrefKey(it)].isNullOrBlank() }.map { it.id }.toSet()
    }

    fun apiKeyFor(provider: AiProvider): Flow<String?> = dataStore.data.map { it[apiKeyPrefKey(provider)] }

    /** Saving a key also makes that provider the active one -- the user just configured it, the
     * obvious assumption is they want to use it now, not leave the previous provider active. */
    suspend fun saveApiKey(provider: AiProvider, key: String) {
        dataStore.edit { prefs ->
            prefs[apiKeyPrefKey(provider)] = key
            prefs[ACTIVE_PROVIDER] = provider.id
        }
    }

    suspend fun clearApiKey(provider: AiProvider) {
        dataStore.edit { prefs ->
            prefs.remove(apiKeyPrefKey(provider))
            if (prefs[ACTIVE_PROVIDER] == provider.id) prefs.remove(ACTIVE_PROVIDER)
        }
    }

    suspend fun setActiveProvider(provider: AiProvider) {
        dataStore.edit { it[ACTIVE_PROVIDER] = provider.id }
    }

    /** One-shot, not a StateFlow -- see AiChatViewModel/HomeViewModel's doc comments on why a
     * continuously-observed StateFlow driving a click/redirect decision is the wrong tool here
     * (its seed value races the real DataStore read). Returns null if there's no active provider
     * or its key is missing/blank. */
    suspend fun currentActiveProviderAndKey(): Pair<AiProvider, String>? {
        val provider = activeProvider.first() ?: return null
        val key = apiKeyFor(provider).first()
        if (key.isNullOrBlank()) return null
        return provider to key
    }
}
