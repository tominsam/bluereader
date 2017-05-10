package org.movieos.feeder;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;

import com.squareup.otto.Subscribe;

import org.movieos.feeder.fragment.EntriesFragment;
import org.movieos.feeder.fragment.LoginFragment;
import org.movieos.feeder.utilities.Settings;
import org.movieos.feeder.utilities.SyncTask;

public class MainActivity extends AppCompatActivity {

    Snackbar mSnackbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FeederApplication.getBus().register(this);
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FeederApplication.getBus().unregister(this);
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void syncStatus(SyncTask.SyncStatus status) {
        if (mSnackbar != null) {
            mSnackbar.setText(status.getStatus());
            mSnackbar.show();
        } else {
            mSnackbar = Snackbar.make(this.findViewById(R.id.main_content), status.getStatus(), Snackbar.LENGTH_INDEFINITE);
            mSnackbar.show();
        }
        if (status.isComplete()) {
            mSnackbar.dismiss();
        }
    }

}
