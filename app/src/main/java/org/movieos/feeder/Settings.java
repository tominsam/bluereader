package org.movieos.feeder;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;

public class Settings {

    public static void saveCredentials(Context context, String credentials) {
        final SharedPreferences prefs = context.getSharedPreferences("credentials", Context.MODE_PRIVATE);
        prefs.edit().putString("credentials", credentials).apply();
    }

    @Nullable
    public static String getCredentials(Context context) {
        final SharedPreferences prefs = context.getSharedPreferences("credentials", Context.MODE_PRIVATE);
        return prefs.getString("credentials", null);
    }



}
