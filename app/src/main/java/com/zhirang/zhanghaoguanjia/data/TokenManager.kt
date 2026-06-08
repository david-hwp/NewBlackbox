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

    fun isLoggedIn(): Boolean {
        return !getToken().isNullOrEmpty()
    }

    companion object {
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER = "user"

        @Volatile
        private var instance: TokenManager? = null

        fun getInstance(): TokenManager {
            return instance ?: synchronized(this) {
                instance ?: TokenManager().also { instance = it }
            }
        }
    }
}
