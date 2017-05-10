package org.movieos.feeder;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.webkit.WebView;

import com.squareup.otto.Subscribe;

import org.movieos.feeder.fragment.EntriesFragment;
import org.movieos.feeder.fragment.LoginFragment;
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
        new SyncTask(this).start();
        mRealm = Realm.getDefaultInstance();
        FeederApplication.getBus().register(this);

        new WebView(this).loadData("test", null, null);
    }

    @Override
    protected void onStop() {
        super.onStop();
        FeederApplication.getBus().unregister(this);
        mRealm.close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void syncStatus(SyncTask.SyncStatus status) {
        if (mSnackbar != null) {
            mSnackbar.setText(status.getStatus());
            mSnackbar.show();
        } else {
            mSnackbar = Snackbar.make(this.findViewById(R.id.main_content), status.getStatus(), Snackbar.LENGTH_LONG);
            mSnackbar.show();
        }
        if (status.isComplete() && status.getException() == null) {
            mSnackbar.dismiss();
        }
    }

}
