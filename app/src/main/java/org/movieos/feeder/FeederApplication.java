package org.movieos.feeder;

import io.realm.Realm;
import okhttp3.CertificatePinner;
import okhttp3.OkHttpClient;
import timber.log.Timber;

public class FeederApplication extends android.app.Application {

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }

        Realm.init(this);
    }

}
