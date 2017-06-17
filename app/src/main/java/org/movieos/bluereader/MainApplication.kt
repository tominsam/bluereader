package org.movieos.bluereader

import android.webkit.WebView
import com.facebook.stetho.Stetho
import io.realm.Realm
import io.realm.RealmConfiguration
import org.greenrobot.eventbus.EventBus
import timber.log.Timber

class MainApplication : android.app.Application() {

    override fun onCreate() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        WebView(this) // https://issuetracker.google.com/issues/37124582
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Stetho.initializeWithDefaults(this);
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Realm.init(this)
        Realm.setDefaultConfiguration(RealmConfiguration.Builder()
                .schemaVersion(6)
                .deleteRealmIfMigrationNeeded()
                .build())
    }

    companion object {
        val bus = EventBus()
    }

}
