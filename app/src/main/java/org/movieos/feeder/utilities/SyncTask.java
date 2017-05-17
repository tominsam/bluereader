package org.movieos.feeder.utilities;

import android.content.Context;
import android.os.AsyncTask;

import org.movieos.feeder.FeederApplication;
import org.movieos.feeder.api.Feedbin;
import org.movieos.feeder.api.PageLinks;
import org.movieos.feeder.model.Entry;
import org.movieos.feeder.model.LocalState;
import org.movieos.feeder.model.Subscription;
import org.movieos.feeder.model.SyncState;
import org.movieos.feeder.model.Tagging;

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
import io.realm.Sort;
import retrofit2.Response;
import timber.log.Timber;

public class SyncTask extends AsyncTask<Void, String, SyncTask.SyncStatus> {

    public static final Executor SYNC_EXECUTOR = Executors.newSingleThreadExecutor();

    private static final int CATCHUP_SIZE = 10;
    private static final int MAX_ENTRIES_COUNT = 1000;

    Context mContext;
    boolean mPushOnly;

    private SyncTask(Context context, boolean pushOnly) {
        mContext = context;
        mPushOnly = pushOnly;
    }

    public static void sync(Context context, boolean force, boolean pushOnly) {
        Realm realm = Realm.getDefaultInstance();
        SyncState state = SyncState.latest(realm);
        if (state == null || state.isStale() || force || pushOnly) {
            new SyncTask(context, pushOnly).start();
        }
        realm.close();
    }

    private void start() {
        executeOnExecutor(SYNC_EXECUTOR);
    }

    @Override
    protected SyncStatus doInBackground(Void... params) {
        onProgressUpdate("Syncing...");
        Feedbin api = new Feedbin(mContext);
        Realm realm = Realm.getDefaultInstance();
        try {
            // Push state. We store the date we pushed up until.
            pushState(api, realm);

            if (!mPushOnly) {
                // Pull server state
                getSubscriptions(api, realm);
                getTaggings(api, realm);
                getEntries(api, realm);

                // update last sync date to be "now"
                realm.executeTransaction(r -> {
                    r.copyToRealmOrUpdate(new SyncState(1, new Date()));
                });
            }

        } catch (Throwable e) {
            Timber.e(e);
            if (mPushOnly) {
                return new SyncStatus(true, "Failed");
            } else {
                return new SyncStatus(e);
            }
        } finally {
            realm.close();
        }
        return new SyncStatus(true, "Done");
    }

    @Override
    protected void onProgressUpdate(String... values) {
        for (String value : values) {
            FeederApplication.getBus().post(new SyncStatus(false, value));
        }
    }

    @Override
    protected void onPostExecute(SyncStatus syncStatus) {
        FeederApplication.getBus().post(syncStatus);
    }

    private void pushState(Feedbin api, Realm realm) throws IOException {
        publishProgress("Pushing local state");
        Set<Integer> addStarred = new HashSet<>();
        Set<Integer> removeStarred = new HashSet<>();
        Set<Integer> addUnread = new HashSet<>();
        Set<Integer> removeUnread = new HashSet<>();

        // make list solid here so that later changes are predictable
        List<LocalState> localStates = new ArrayList<>(LocalState.all(realm));

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
            realm.executeTransaction(r -> {
                for (Entry entry : r.where(Entry.class).in("mId", addStarred.toArray(new Integer[0])).findAll()) {
                    entry.setStarredFromServer(true);
                }
            });
        }
        if (!removeStarred.isEmpty()) {
            api.removeStarred(removeStarred).execute();
            realm.executeTransaction(r -> {
                for (Entry entry : r.where(Entry.class).in("mId", removeStarred.toArray(new Integer[0])).findAll()) {
                    entry.setStarredFromServer(false);
                }
            });
        }
        if (!addUnread.isEmpty()) {
            api.addUnread(addUnread).execute();
            realm.executeTransaction(r -> {
                for (Entry entry : r.where(Entry.class).in("mId", addUnread.toArray(new Integer[0])).findAll()) {
                    entry.setUnreadFromServer(true);
                }
            });
        }
        if (!removeUnread.isEmpty()) {
            api.removeUnread(removeUnread).execute();
            realm.executeTransaction(r -> {
                for (Entry entry : r.where(Entry.class).in("mId", removeUnread.toArray(new Integer[0])).findAll()) {
                    entry.setUnreadFromServer(false);
                }
            });
        }

        if (!localStates.isEmpty()) {
            realm.executeTransaction(r -> {
                for (LocalState localState : localStates) {
                    localState.deleteFromRealm();
                }
            });
        }
    }

    private void getSubscriptions(Feedbin api, Realm realm) throws IOException {
        publishProgress("Syncing subscriptions");
        Subscription latestSubscription = realm.where(Subscription.class).findAllSorted("mCreatedAt", Sort.DESCENDING).first(null);
        Date subscriptionsSince = latestSubscription == null ? null : latestSubscription.getCreatedAt();
        Response<List<Subscription>> subscriptions = api.subscriptions(subscriptionsSince).execute();
        realm.executeTransaction(r -> r.copyToRealmOrUpdate(subscriptions.body()));
    }

    private void getTaggings(Feedbin api, Realm realm) throws IOException {
        publishProgress("Syncing tags");
        Response<List<Tagging>> taggings = api.taggings().execute();
        realm.executeTransaction(r -> r.copyToRealmOrUpdate(taggings.body()));
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

        // Finally, if there's anything in the unread or starred lists we haven't seen,
        // fetch those explicitly.
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
            publishProgress("Backfilling " + missing.size() + " entries");
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
