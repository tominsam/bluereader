package org.movieos.feeder

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.webkit.WebView
import io.realm.Realm
import org.movieos.feeder.fragment.EntriesFragment
import org.movieos.feeder.fragment.LoginFragment
import org.movieos.feeder.utilities.Settings
import org.movieos.feeder.utilities.SyncTask

class MainActivity : AppCompatActivity() {

    // control the lifetime of the realm object
    internal var realm: Realm? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .add(R.id.main_content, if (Settings.getCredentials(this) != null) EntriesFragment() else LoginFragment())
                    .commit()
        }
    }

    override fun onStart() {
        super.onStart()
        realm = Realm.getDefaultInstance()
        WebView(this).loadData("test", null, null)
        SyncTask.sync(this, false, false)
    }

    override fun onStop() {
        super.onStop()
        realm?.close()
        realm = null
    }

    override fun onDestroy() {
        super.onDestroy()
    }

}
