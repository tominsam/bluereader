package org.movieos.bluereader

import android.arch.persistence.room.Room
import android.webkit.WebView
import com.facebook.stetho.Stetho
import org.greenrobot.eventbus.EventBus
import org.movieos.bluereader.dao.MainDatabase
import timber.log.Timber


class MainApplication : android.app.Application() {

    val database: MainDatabase by lazy {
        Room.databaseBuilder(applicationContext, MainDatabase::class.java, "bluereader")
                .allowMainThreadQueries()
                .build()
    }

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
    }

    companion object {
        val bus = EventBus()
    }

}
