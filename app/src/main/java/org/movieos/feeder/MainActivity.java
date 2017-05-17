package org.movieos.feeder;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.webkit.WebView;

import org.movieos.feeder.fragment.EntriesFragment;
import org.movieos.feeder.fragment.LoginFragment;
import org.movieos.feeder.utilities.FragmentBackHandler;
import org.movieos.feeder.utilities.Settings;
import org.movieos.feeder.utilities.SyncTask;

import io.realm.Realm;

public class MainActivity extends AppCompatActivity {

    Snackbar mSnackbar;
    // control the lifetime of the realm object
    Realm mRealm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.main_content, Settings.getCredentials(this) != null ? new EntriesFragment() : new LoginFragment())
                .commitNow();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mRealm = Realm.getDefaultInstance();
        new WebView(this).loadData("test", null, null);
        SyncTask.sync(this, false, false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mRealm.close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_content);
        if (fragment instanceof FragmentBackHandler) {
            boolean handled = ((FragmentBackHandler) fragment).onBackPressed();
            if (handled) {
                return;
            }
        }
        super.onBackPressed();
    }

}
