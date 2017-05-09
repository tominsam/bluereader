package org.movieos.feeder;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import org.movieos.feeder.fragment.LoginFragment;
import org.movieos.feeder.fragment.SyncFragment;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.main_content, Settings.getCredentials(this) != null ? new SyncFragment() : new LoginFragment())
                .commitNow();
        }
    }
}
