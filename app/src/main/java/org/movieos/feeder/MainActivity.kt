package org.movieos.feeder

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.webkit.WebView
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import io.realm.Realm
import org.movieos.feeder.fragment.EntriesFragment
import org.movieos.feeder.fragment.LoginFragment
import org.movieos.feeder.utilities.Settings
import org.movieos.feeder.utilities.SyncTask

class MainActivity : AppCompatActivity(), GoogleApiClient.OnConnectionFailedListener {

    // control the lifetime of the realm object
    internal var realm: Realm? = null

    val client by lazy {
        GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(Auth.CREDENTIALS_API)
                .build()
    }


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
        SyncTask.sync(this, false, true)
        realm?.close()
        realm = null
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onConnectionFailed(p0: ConnectionResult) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val fragment = supportFragmentManager.findFragmentById(R.id.main_content)
        fragment.onActivityResult(requestCode, resultCode, data)
    }
}
