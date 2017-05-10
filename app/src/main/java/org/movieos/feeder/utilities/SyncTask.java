package org.movieos.feeder.utilities;

import android.content.Context;
import android.os.AsyncTask;

import org.movieos.feeder.FeederApplication;
import org.movieos.feeder.api.Feedbin;
import org.movieos.feeder.api.PageLinks;
import org.movieos.feeder.sync.Entry;
import org.movieos.feeder.sync.Subscription;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.realm.Realm;
import io.realm.Sort;
import retrofit2.Response;
import timber.log.Timber;

public class SyncTask extends AsyncTask<Void, String, Void> {

    public static final Executor SYNC_EXECUTOR = Executors.newSingleThreadExecutor();

    Context mContext;

    public SyncTask(Context context) {
        mContext = context;
    }

    public void start() {
        executeOnExecutor(SYNC_EXECUTOR);
    }

    @Override
    protected Void doInBackground(Void... params) {
        Feedbin api = new Feedbin(mContext);
        Realm realm = Realm.getDefaultInstance();
        try {

            publishProgress("Syncing unread state");
            List<Integer> unread = api.unread().execute().body();

            publishProgress("Syncing starred state");
            List<Integer> starred = api.starred().execute().body();

            int subscriptionCount = 0;
            publishProgress("Syncing subscriptions");
            Subscription latestSubscription = realm.where(Subscription.class).findAllSorted("mCreatedAt", Sort.DESCENDING).first(null);
            Date subscriptionsSince = latestSubscription == null ? null : latestSubscription.getCreatedAt();
            Response<List<Subscription>> subscriptions = api.subscriptions(subscriptionsSince).execute();
            while (true) {
                Response<List<Subscription>> finalResponse = subscriptions;
                realm.executeTransaction(r -> r.copyToRealmOrUpdate(finalResponse.body()));
                PageLinks links = new PageLinks(subscriptions.raw());
                if (links.getNext() == null) {
                    break;
                }
                subscriptionCount += subscriptions.body().size();
                publishProgress(String.format(Locale.getDefault(), "Syncing subscriptions (%d)", subscriptionCount));
                subscriptions = api.subscriptionsPaginate(links.getNext()).execute();
            }

            int entryCount = 0;
            publishProgress("Syncing entries");
            Entry latestEntry = realm.where(Entry.class).findAllSorted("mCreatedAt", Sort.DESCENDING).first(null);
            Date entriesSince = latestEntry == null ? null : latestEntry.getCreatedAt();
            Response<List<Entry>> entries = api.entries(entriesSince).execute();
            while (true) {
                Response<List<Entry>> finalResponse = entries;
                for (Entry entry : finalResponse.body()) {
                    entry.setUnread(unread.contains(entry.getId()), false);
                    entry.setStarred(starred.contains(entry.getId()), false);
                    entry.setSubscription(realm.where(Subscription.class).equalTo("mFeedId", entry.getFeedId()).findFirst());
                }
                realm.executeTransaction(r -> r.copyToRealmOrUpdate(finalResponse.body()));
                PageLinks links = new PageLinks(entries.raw());
                if (links.getNext() == null) {
                    break;
                }
                entryCount += entries.body().size();
                publishProgress(String.format(Locale.getDefault(), "Syncing entries (%d)", entryCount));
                entries = api.entriesPaginate(links.getNext()).execute();
                if (entryCount >= 1000) {
                    // TODO handle better
                    break;
                }
            }

            // TODO first sync

        } catch (IOException e) {
            // TODO
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        for (String value : values) {
            Timber.i(value);
            FeederApplication.getBus().post(new SyncStatus(false, value));
        }
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        FeederApplication.getBus().post(new SyncStatus(true, "Done"));
    }

    public static class SyncStatus {
        boolean complete;
        String status;

        public SyncStatus(boolean complete, String status) {
            this.complete = complete;
            this.status = status;
        }

        public boolean isComplete() {
            return complete;
        }

        public String getStatus() {
            return status;
        }
    }
}
