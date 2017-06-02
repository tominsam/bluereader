package org.movieos.bluereader

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.webkit.WebView
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import io.realm.Realm
import org.movieos.bluereader.fragment.EntriesFragment
import org.movieos.bluereader.fragment.LoginFragment
import org.movieos.bluereader.utilities.Settings
import org.movieos.bluereader.utilities.SyncTask
import timber.log.Timber

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
        setTheme(R.style.AppTheme)

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
        // No smart lock, don't really care much
        Timber.i("Google client connection failed: $p0")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val fragment = supportFragmentManager.findFragmentById(R.id.main_content)
        fragment.onActivityResult(requestCode, resultCode, data)
    }
}
