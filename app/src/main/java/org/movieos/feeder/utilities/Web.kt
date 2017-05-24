package org.movieos.feeder.utilities

import android.app.Activity
import android.net.Uri
import android.support.customtabs.CustomTabsIntent
import android.support.v4.content.ContextCompat
import org.movieos.feeder.R

class Web {

    companion object {

        fun openInBrowser(activity: Activity, url: String?) {
            val builder = CustomTabsIntent.Builder()
            builder.setToolbarColor(ContextCompat.getColor(activity, R.color.primary))
            builder.addDefaultShareMenuItem()
            val customTabsIntent = builder.build()
            customTabsIntent.launchUrl(activity, Uri.parse(url))
        }

    }
}
