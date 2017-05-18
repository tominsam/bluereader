package org.movieos.feeder.utilities

import android.content.Context

object Settings {

    fun saveCredentials(context: Context, credentials: String) {
        val prefs = context.getSharedPreferences("credentials", Context.MODE_PRIVATE)
        prefs.edit().putString("credentials", credentials).apply()
    }

    fun getCredentials(context: Context): String? {
        val prefs = context.getSharedPreferences("credentials", Context.MODE_PRIVATE)
        return prefs.getString("credentials", null)
    }


}
