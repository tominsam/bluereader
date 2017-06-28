package org.movieos.bluereader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDelegate
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import org.movieos.bluereader.fragment.EntriesFragment
import org.movieos.bluereader.fragment.LoginFragment
import org.movieos.bluereader.utilities.Settings
import org.movieos.bluereader.utilities.SyncTask
import timber.log.Timber

class MainActivity : AppCompatActivity(), GoogleApiClient.OnConnectionFailedListener {

    val client: GoogleApiClient by lazy {
        GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(Auth.CREDENTIALS_API)
                .build()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(
                when (wantNightMode()) {
                    true -> AppCompatDelegate.MODE_NIGHT_YES
                    false -> AppCompatDelegate.MODE_NIGHT_NO
                }
        )
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

    fun wantNightMode() = getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("night_mode", false)

    override fun onStart() {
        super.onStart()
        SyncTask.sync(this, false, false)
    }

    override fun onStop() {
        super.onStop()
        SyncTask.sync(this, false, true)
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

    override fun onBackPressed() {
        val fragment = supportFragmentManager.findFragmentById(R.id.main_content)
        if (fragment is EntriesFragment) {
            if (fragment.onBackPressed()) {
                return
            }
        }
        super.onBackPressed()
    }
}
