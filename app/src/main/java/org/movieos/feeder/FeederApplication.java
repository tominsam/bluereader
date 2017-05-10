package org.movieos.feeder;

import android.webkit.WebView;

import com.squareup.otto.Bus;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import timber.log.Timber;

public class FeederApplication extends android.app.Application {
    static Bus sOtto = new Bus();

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }

        Realm.init(this);

        RealmConfiguration config = new RealmConfiguration.Builder()
            .schemaVersion(3)
            .deleteRealmIfMigrationNeeded()
            .build();

        Realm.setDefaultConfiguration(config);

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
    }

    public static Bus getBus() {
        return sOtto;
    }

}
