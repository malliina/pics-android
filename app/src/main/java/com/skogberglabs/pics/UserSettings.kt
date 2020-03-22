package com.skogberglabs.pics

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.skogberglabs.pics.backend.Email
import com.skogberglabs.pics.backend.Json
import com.skogberglabs.pics.backend.UserInfo
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import timber.log.Timber

@JsonClass(generateAdapter = true)
data class PrivateEmail(val email: Email?)

@JsonClass(generateAdapter = true)
data class IsPrivate(val isPrivate: Boolean)

class UserSettings(private val prefs: SharedPreferences) {
    companion object {
        fun load(ctx: Context): UserSettings {
            val prefs = ctx.getSharedPreferences(
                "com.skogberglabs.prefs",
                Context.MODE_PRIVATE
            )
            return UserSettings(prefs)
        }

        val emailAdapter: JsonAdapter<PrivateEmail> = Json.moshi.adapter(PrivateEmail::class.java)
        val isPrivateAdapter: JsonAdapter<IsPrivate> = Json.moshi.adapter(IsPrivate::class.java)
    }

    private val privateEmailKey = "private_email"
    var privateEmail: Email?
        set(value) = save(PrivateEmail(value), emailAdapter, privateEmailKey)
        get() = load(privateEmailKey, emailAdapter, PrivateEmail(null)).email

    private val isPrivateKey = "is_private"
    var isPrivate: Boolean
        set(value) = save(IsPrivate(value), isPrivateAdapter, isPrivateKey)
        get() = load(isPrivateKey, isPrivateAdapter, IsPrivate(false)).isPrivate

    private fun <T> load(key: String, adapter: JsonAdapter<T>, default: T): T {
        return loadOpt(key, adapter) ?: default
    }

    private fun <T> loadOpt(key: String, adapter: JsonAdapter<T>): T? {
        val str = prefs.getString(key, null)
        return str?.let { adapter.fromJson(it) }
    }

    private fun <T> save(item: T, adapter: JsonAdapter<T>, to: String) {
        prefs.edit {
            val json = adapter.toJson(item)
            putString(to, json)
            Timber.i("Saved $json.")
        }
    }
}
