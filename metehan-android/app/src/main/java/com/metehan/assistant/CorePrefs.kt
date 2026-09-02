package com.metehan.assistant

import android.content.Context
import android.net.Uri

object CorePrefs {
    private const val PREFS = "metehan"
    private const val KEY_CORE_URL = "core_url"
    private const val KEY_TOKEN = "access_token"
    const val DEFAULT_CORE_URL = "http://127.0.0.1:8765"

    fun coreUrl(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_CORE_URL, DEFAULT_CORE_URL)!!.trimEnd('/')

    fun accessToken(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_TOKEN, "") ?: ""

    fun save(context: Context, url: String, token: String) {
        require(isSafeUrl(url)) { "Uzak Metehan çekirdeği HTTPS kullanmalı." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CORE_URL, url.trimEnd('/'))
            .putString(KEY_TOKEN, token)
            .apply()
    }

    fun isSafeUrl(value: String): Boolean {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
        if (uri.scheme == "https") return true
        val host = uri.host ?: return false
        return uri.scheme == "http" && (host == "127.0.0.1" || host == "localhost")
    }
}
