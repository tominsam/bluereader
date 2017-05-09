package org.movieos.feeder.sync;

import android.content.Context;

import io.realm.Realm;
import io.realm.Sort;
import org.movieos.feeder.api.Feedbin;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.Date;
import java.util.List;

public class Sync {
    Context mContext;

    public Sync(Context context) {
        mContext = context;
    }

    public void subscriptions() {

        Realm realm = Realm.getDefaultInstance();
        Subscription latest = realm.where(Subscription.class).findAllSorted("mCreatedAt", Sort.DESCENDING).first(null);
        Date since = latest == null ? null : latest.getCreatedAt();

        int page = 1;

        new Feedbin(mContext)
            .subscriptions(since, page)
            .enqueue(new Callback<List<Subscription>>() {
                @Override
                public void onResponse(Call<List<Subscription>> call, Response<List<Subscription>> response) {
                    Realm.getDefaultInstance().executeTransaction(r -> {
                        r.copyToRealmOrUpdate(response.body());
                    });
                }

                @Override
                public void onFailure(Call<List<Subscription>> call, Throwable t) {

                }
            });
    }
}
