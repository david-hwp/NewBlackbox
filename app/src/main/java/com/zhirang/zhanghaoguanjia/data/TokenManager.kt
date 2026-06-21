package com.zhirang.zhanghaoguanjia.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.zhirang.zhanghaoguanjia.app.App
import com.zhirang.zhanghaoguanjia.bean.dto.UserDto

class TokenManager private constructor() {

    private val prefs: SharedPreferences
    private val gson = Gson()

    init {
        val context = App.getContext()
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun saveUser(user: UserDto) {
        val json = gson.toJson(user.normalizedForStorage())
        prefs.edit().putString(KEY_USER, json).apply()
    }

    fun getUser(): UserDto? {
        val json = prefs.getString(KEY_USER, null) ?: return null
        return try {
            gson.fromJson(json, UserDto::class.java)?.normalizedForStorage()
        } catch (e: Exception) {
            null
        }
    }

    fun clearUser() {
        prefs.edit().remove(KEY_USER).apply()
    }

    fun getLegacyEngineMigrationState(userId: Long): String? {
        return prefs.getString(legacyMigrationKey(userId), null)
    }

    fun saveLegacyEngineMigrationState(userId: Long, state: String) {
        prefs.edit().putString(legacyMigrationKey(userId), state).apply()
    }

    fun getLegacyEngineMigrationPending(userId: Long): String? {
        return prefs.getString(legacyMigrationPendingKey(userId), null)
    }

    fun saveLegacyEngineMigrationPending(userId: Long, pendingJson: String) {
        prefs.edit().putString(legacyMigrationPendingKey(userId), pendingJson).apply()
    }

    fun clearLegacyEngineMigrationPending(userId: Long) {
        prefs.edit().remove(legacyMigrationPendingKey(userId)).apply()
    }

    fun armLegacyEngineMigrationAfterLogin(userId: Long) {
        prefs.edit().putBoolean(legacyMigrationLoginTriggerKey(userId), true).apply()
    }

    fun hasLegacyEngineMigrationAfterLogin(userId: Long): Boolean {
        return prefs.getBoolean(legacyMigrationLoginTriggerKey(userId), false)
    }

    fun consumeLegacyEngineMigrationAfterLogin(userId: Long): Boolean {
        val key = legacyMigrationLoginTriggerKey(userId)
        if (!prefs.getBoolean(key, false)) {
            return false
        }
        prefs.edit().remove(key).apply()
        return true
    }

    fun clearLegacyEngineMigrationAfterLogin(userId: Long) {
        prefs.edit().remove(legacyMigrationLoginTriggerKey(userId)).apply()
    }

    fun isLoggedIn(): Boolean {
        return !getToken().isNullOrEmpty()
    }

    private fun legacyMigrationKey(userId: Long): String {
        return "$KEY_LEGACY_ENGINE_MIGRATION_PREFIX$userId"
    }

    private fun legacyMigrationPendingKey(userId: Long): String {
        return "$KEY_LEGACY_ENGINE_MIGRATION_PENDING_PREFIX$userId"
    }

    private fun legacyMigrationLoginTriggerKey(userId: Long): String {
        return "$KEY_LEGACY_ENGINE_MIGRATION_LOGIN_TRIGGER_PREFIX$userId"
    }

    companion object {
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER = "user"
        private const val KEY_LEGACY_ENGINE_MIGRATION_PREFIX = "legacy_engine_migration_"
        private const val KEY_LEGACY_ENGINE_MIGRATION_PENDING_PREFIX = "legacy_engine_migration_pending_"
        private const val KEY_LEGACY_ENGINE_MIGRATION_LOGIN_TRIGGER_PREFIX = "legacy_engine_migration_login_trigger_"

        const val LEGACY_ENGINE_MIGRATION_SUCCESS = "success"
        const val LEGACY_ENGINE_MIGRATION_UNSUPPORTED = "unsupported"
        const val LEGACY_ENGINE_MIGRATION_FAILED = "failed"
        const val LEGACY_ENGINE_MIGRATION_IN_PROGRESS = "in_progress"

        @Volatile
        private var instance: TokenManager? = null

        fun getInstance(): TokenManager {
            return instance ?: synchronized(this) {
                instance ?: TokenManager().also { instance = it }
            }
        }
    }
}
