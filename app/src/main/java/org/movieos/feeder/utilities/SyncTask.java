package org.movieos.feeder.utilities;

import android.content.Context;
import android.os.AsyncTask;

import org.movieos.feeder.FeederApplication;
import org.movieos.feeder.api.Feedbin;
import org.movieos.feeder.api.PageLinks;
import org.movieos.feeder.model.Entry;
import org.movieos.feeder.model.LocalState;
import org.movieos.feeder.model.Subscription;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;
import retrofit2.Response;
import timber.log.Timber;

public class SyncTask extends AsyncTask<Void, String, SyncTask.SyncStatus> {

    public static final Executor SYNC_EXECUTOR = Executors.newSingleThreadExecutor();

    private static final int CATCHUP_SIZE = 10;
    private static final int MAX_ENTRIES_COUNT = 1000;

    Context mContext;

    public SyncTask(Context context) {
        mContext = context;
    }

    public void start() {
        executeOnExecutor(SYNC_EXECUTOR);
    }

    @Override
    protected SyncStatus doInBackground(Void... params) {
        Timber.i("Syncing..");
        Feedbin api = new Feedbin(mContext);
        Realm realm = Realm.getDefaultInstance();
        try {
            // TODO first sync - the entries are returned in reverse order, so we need
            // to only mark the sync as complete once we have fetched enough, or we can
            // never paginate back there again

            Date state = pushState(api, realm);

            getSubscriptions(api, realm);
            getEntries(api, realm);

            // remove local state after we've pulled server state, so stars don't blink
            // on and off during sync.
            if (state != null) {
                realm.executeTransaction(r -> {
                    LocalState.all(r).where().lessThanOrEqualTo("mTimeStamp", state).findAll().deleteAllFromRealm();
                });
            }

        } catch (Throwable e) {
            Timber.e(e);
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

    private Date pushState(Feedbin api, Realm realm) throws IOException {
        publishProgress("Pushing local state");
        Set<Integer> addStarred = new HashSet<>();
        Set<Integer> removeStarred = new HashSet<>();
        Set<Integer> addUnread = new HashSet<>();
        Set<Integer> removeUnread = new HashSet<>();

        RealmResults<LocalState> localStates = LocalState.all(realm);

        realm.executeTransaction(r -> {
            for (LocalState localState : localStates) {
                int id = localState.getEntryId();
                if (localState.getMarkStarred() != null) {
                    addStarred.remove(id);
                    removeStarred.remove(id);
                    (localState.getMarkStarred() ? addStarred : removeStarred).add(id);
                }
                if (localState.getMarkUnread() != null) {
                    addUnread.remove(id);
                    removeUnread.remove(id);
                    (localState.getMarkUnread() ? addUnread : removeUnread).add(id);
                }
            }
        });

        if (!addStarred.isEmpty()) {
            api.addStarred(addStarred).execute();
        }
        if (!removeStarred.isEmpty()) {
            api.removeStarred(removeStarred).execute();
        }
        if (!addUnread.isEmpty()) {
            api.addUnread(addUnread).execute();
        }
        if (!removeUnread.isEmpty()) {
            api.removeUnread(removeUnread).execute();
        }

        if (localStates.isEmpty()) {
            return null;
        } else {
            return localStates.last().getTimeStamp();
        }
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
        // We need these first so we can add new entries in the right state
        publishProgress("Syncing unread state");
        List<Integer> unread = api.unread().execute().body();
        publishProgress("Syncing starred state");
        List<Integer> starred = api.starred().execute().body();

        int entryCount = 0;
        publishProgress("Syncing entries");
        Entry latestEntry = realm.where(Entry.class).findAllSorted("mCreatedAt", Sort.DESCENDING).first(null);
        Date entriesSince = latestEntry == null ? null : latestEntry.getCreatedAt();
        if (Entry.entries(realm, Entry.ViewType.ALL).size() < MAX_ENTRIES_COUNT) {
            // ignore the incremental sync stuff until we have at least
            // this many entries - this fixes the problem if the first sync
            // fails.
            entriesSince = null;
        }
        Response<List<Entry>> entries = api.entries(entriesSince).execute();
        while (true) {
            Response<List<Entry>> finalResponse = entries;
            insertEntries(realm, unread, starred, finalResponse);
            PageLinks links = new PageLinks(entries.raw());
            if (links.getNext() == null) {
                break;
            }
            entryCount += entries.body().size();
            publishProgress(String.format(Locale.getDefault(), "Syncing entries (%d)", entryCount));
            entries = api.entriesPaginate(links.getNext()).execute();
            if (entryCount >= MAX_ENTRIES_COUNT) {
                // TODO handle better
                break;
            }
        }

        // Now we need to re-up everything in the database, because there's no way of knowing
        // if the server changed the starred / unread state of something - we won't have seen
        // it in the API response if it's not new.
        realm.executeTransaction(r -> {
            for (Entry entry : r.where(Entry.class).findAll()) {
                entry.setUnreadFromServer(unread.contains(entry.getId()));
                entry.setStarredFromServer(starred.contains(entry.getId()));
            }
        });

        List<Integer> missing = new ArrayList<>();
        for (Integer integer : unread) {
            if (realm.where(Entry.class).equalTo("mId", integer).findAll().size() == 0) {
                missing.add(integer);
            }
        }
        for (Integer integer : starred) {
            if (realm.where(Entry.class).equalTo("mId", integer).findAll().size() == 0) {
                missing.add(integer);
            }
        }
        while (!missing.isEmpty()) {
            List<Integer> page = ListUtils.slice(0, CATCHUP_SIZE, missing);
            missing = ListUtils.slice(CATCHUP_SIZE, missing.size(), missing);
            Response<List<Entry>> missingEntries = api.entries(page).execute();
            insertEntries(realm, unread, starred, missingEntries);
        }

    }

    private void insertEntries(Realm realm, List<Integer> unread, List<Integer> starred, Response<List<Entry>> finalResponse) {
        for (Entry entry : finalResponse.body()) {
            // Connect entries to their subscriptions
            entry.setSubscription(realm.where(Subscription.class).equalTo("mFeedId", entry.getFeedId()).findFirst());
            // Create entries with the right read/unread state
            entry.setStarredFromServer(starred.contains(entry.getId()));
            entry.setUnreadFromServer(unread.contains(entry.getId()));
        }
        realm.executeTransaction(r -> r.copyToRealmOrUpdate(finalResponse.body()));
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
            mStatus = exception.getMessage();
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
