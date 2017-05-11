package org.movieos.feeder;

import android.webkit.WebView;

import org.greenrobot.eventbus.EventBus;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import timber.log.Timber;

public class FeederApplication extends android.app.Application {
    static EventBus sEventBus = new EventBus();

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }

        Realm.init(this);

        RealmConfiguration config = new RealmConfiguration.Builder()
            .schemaVersion(6)
            .deleteRealmIfMigrationNeeded()
            .build();

        Realm.setDefaultConfiguration(config);

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
    }

    public static EventBus getBus() {
        return sEventBus;
    }

}
