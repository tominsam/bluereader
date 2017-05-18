package org.movieos.feeder

import android.webkit.WebView
import io.realm.Realm
import io.realm.RealmConfiguration
import org.greenrobot.eventbus.EventBus
import timber.log.Timber

class FeederApplication : android.app.Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Realm.init(this)

        val config = RealmConfiguration.Builder()
                .schemaVersion(6)
                .deleteRealmIfMigrationNeeded()
                .build()

        Realm.setDefaultConfiguration(config)

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }

    companion object {
        var bus = EventBus()
            internal set
    }

}
