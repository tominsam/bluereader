package org.movieos.bluereader

import android.arch.persistence.room.Room
import android.util.Log
import android.webkit.WebView
import com.facebook.stetho.Stetho
import com.google.firebase.crash.FirebaseCrash
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

        } else {
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String?, throwable: Throwable?) {
                    if (priority == Log.VERBOSE || priority == Log.DEBUG || priority == Log.INFO) {
                        return
                    }
                    if (message != null) {
                        FirebaseCrash.log("$tag - $message")
                    }
                    if (throwable != null) {
                        FirebaseCrash.report(throwable)
                    }
                }
            })
        }
    }

    companion object {
        val bus = EventBus()
    }

}
