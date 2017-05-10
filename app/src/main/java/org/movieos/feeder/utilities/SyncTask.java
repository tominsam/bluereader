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
import io.realm.RealmResults;
import io.realm.Sort;
import retrofit2.Response;
import timber.log.Timber;

public class SyncTask extends AsyncTask<Void, String, SyncTask.SyncStatus> {

    public static final Executor SYNC_EXECUTOR = Executors.newSingleThreadExecutor();

    Context mContext;

    public SyncTask(Context context) {
        mContext = context;
    }

    public void start() {
        executeOnExecutor(SYNC_EXECUTOR);
    }

    @Override
    protected SyncStatus doInBackground(Void... params) {
        Feedbin api = new Feedbin(mContext);
        Realm realm = Realm.getDefaultInstance();
        try {
            // TODO first sync - the entries are returned in reverse order, so we need
            // to only mark the sync as complete once we have fetched enough, or we can
            // never paginate back there again
            pushState(api, realm);
            getSubscriptions(api, realm);
            getEntries(api, realm);
            getState(api, realm);
        } catch (Throwable e) {
            return new SyncStatus(e);
        } finally {
            realm.close();
        }
        return new SyncStatus(true, "Done");
    }

    @Override
    protected void onProgressUpdate(String... values) {
        for (String value : values) {
            Timber.i(value);
            FeederApplication.getBus().post(new SyncStatus(false, value));
        }
    }

    @Override
    protected void onPostExecute(SyncStatus syncStatus) {
        FeederApplication.getBus().post(syncStatus);
    }

    private void pushState(Feedbin api, Realm realm) throws IOException {
        publishProgress("Pushing local state");
        RealmResults<Entry> dirty = realm.where(Entry.class).equalTo("mStarredDirty", true).findAll();
        Timber.i("Seen %d dirty entries", dirty.size());
        while (dirty.size() > 0) {
            List<Integer> add = ListUtils.map(Entry::getId, ListUtils.filter(Entry::isStarred, dirty));
            List<Integer> remove = ListUtils.map(Entry::getId, ListUtils.filter(e -> !e.isStarred(), dirty));
            // Now we have the list of IDs, unset the dirty flag. If anything sets it back,
            // we'll pick it up next time round the loop.
            realm.executeTransaction(r -> {
                for (Entry entry : dirty) {
                    entry.setStarred(entry.isStarred(), false);
                }
            });
            api.addStarred(add).execute();
            api.removeStarred(remove).execute();
        }
        realm.close();
    }

    private void getSubscriptions(Feedbin api, Realm realm) throws IOException {
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
    }

    private void getEntries(Feedbin api, Realm realm) throws IOException {
        int entryCount = 0;
        publishProgress("Syncing entries");
        Entry latestEntry = realm.where(Entry.class).findAllSorted("mCreatedAt", Sort.DESCENDING).first(null);
        Date entriesSince = latestEntry == null ? null : latestEntry.getCreatedAt();
        Response<List<Entry>> entries = api.entries(entriesSince).execute();
        while (true) {
            Response<List<Entry>> finalResponse = entries;
            for (Entry entry : finalResponse.body()) {
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
    }

    private void getState(Feedbin api, Realm realm) throws IOException {
        publishProgress("Syncing unread state");
        List<Integer> unread = api.unread().execute().body();

        publishProgress("Syncing starred state");
        List<Integer> starred = api.starred().execute().body();

        // Need to set these states after sync - marking an entry as read/unread won't
        // re-fetch it.
        realm.executeTransaction(r -> {
            for (Integer unreadId : unread) {
                Entry entry = r.where(Entry.class).equalTo("mId", unreadId).findFirst();
                if (entry != null) {
                    entry.setUnread(true, false);
                }
            }
            for (Integer starredId : starred) {
                Entry entry = r.where(Entry.class).equalTo("mId", starredId).findFirst();
                if (entry != null) {
                    entry.setStarred(true, false);
                }
            }
            for (Entry entry : r.where(Entry.class).equalTo("mUnread", true).findAll()) {
                if (!unread.contains(entry.getId())) {
                    entry.setUnread(false, false);
                }
            }
            for (Entry entry : r.where(Entry.class).equalTo("mStarred", true).findAll()) {
                if (!starred.contains(entry.getId())) {
                    entry.setStarred(false, false);
                }
            }
        });
    }

    public static class SyncStatus {
        boolean mComplete;
        String mStatus;
        Throwable mException;

        public SyncStatus(boolean complete, String status) {
            mComplete = complete;
            mStatus = status;
            mException = null;
        }

        public SyncStatus(Throwable exception) {
            mComplete = true;
            mException = exception;
            mStatus = exception.getLocalizedMessage();
        }

        public boolean isComplete() {
            return mComplete;
        }

        public String getStatus() {
            return mStatus;
        }

        public Throwable getException() {
            return mException;
        }
    }
}
