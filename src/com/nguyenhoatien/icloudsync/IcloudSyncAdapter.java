package com.nguyenhoatien.icloudsync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;
import java.io.IOException;
import java.util.List;

public class IcloudSyncAdapter extends AbstractThreadedSyncAdapter {

    static final String TAG = "IcloudSync";

    private final AccountManager accounts;

    public IcloudSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        this.accounts = AccountManager.get(context);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {
        CrashLog.install(getContext());
        SyncLog.add("onPerformSync ENTERED for " + account.name
                + " authority=" + authority + " manual="
                + extras.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false));

        String password;
        try {
            password = accounts.getPassword(account);
        } catch (SecurityException e) {
            SyncLog.add("getPassword DENIED: " + e.getMessage());
            syncResult.stats.numAuthExceptions++;
            return;
        }
        SyncLog.add("password read back from AccountManager, len="
                + (password == null ? -1 : password.length()));
        if (password == null || password.length() == 0) {
            SyncLog.add("ABORT: no password stored for " + account.name);
            syncResult.stats.numAuthExceptions++;
            return;
        }

        try {
            CardDavClient client = new CardDavClient(
                    CardDavClient.DEFAULT_ROOT, account.name, password);
            List<VCardContact> contacts = client.fetchContacts();

            ContactsWriter writer = new ContactsWriter(
                    getContext().getContentResolver());
            ContactsWriter.Result r = writer.replaceAll(account.name, contacts);
            SyncLog.add("RESULT deleted=" + r.deleted + " inserted=" + r.inserted
                    + " skipped=" + r.skipped);

            if (contacts.isEmpty()) {
                SyncLog.add("WARNING: 0 contacts fetched, nothing written");
                // Nothing was written, so report it as a soft error rather than
                // a clean sync: an empty answer is far more likely a server
                // hiccup than a genuinely emptied address book.
                syncResult.stats.numIoExceptions++;
            }
        } catch (CardDavClient.DavException e) {
            SyncLog.add("DAV ERROR " + e.status + ": " + e.getMessage());
            if (e.isAuthFailure()) {
                syncResult.stats.numAuthExceptions++;
            } else {
                syncResult.stats.numIoExceptions++;
            }
        } catch (IOException e) {
            SyncLog.add("NETWORK ERROR", e);
            syncResult.stats.numIoExceptions++;
        } catch (Exception e) {
            // A parse or provider failure would otherwise kill the sync thread
            // and be retried forever, so mark it hard and let it drop.
            SyncLog.add("SYNC FAILED", e);
            syncResult.databaseError = true;
        }
    }

    public static void requestSync(Account account) {
        Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        String authority = android.provider.ContactsContract.AUTHORITY;
        SyncLog.add("requestSync " + account.name
                + " syncable=" + stat(account, authority, "syncable")
                + " auto=" + stat(account, authority, "auto")
                + " masterAuto=" + stat(account, authority, "master")
                + " pending=" + stat(account, authority, "pending")
                + " active=" + stat(account, authority, "active"));
        ContentResolver.requestSync(account, authority, extras);
    }

    public static void enableAutoSync(Account account) {
        String authority = android.provider.ContactsContract.AUTHORITY;
        try {
            ContentResolver.setIsSyncable(account, authority, 1);
            ContentResolver.setSyncAutomatically(account, authority, true);
            ContentResolver.addPeriodicSync(account, authority, new Bundle(), 3600L);
            SyncLog.add("enableAutoSync done, syncable="
                    + stat(account, authority, "syncable"));
        } catch (SecurityException e) {
            // Losing periodic sync is survivable; the manual button still works.
            SyncLog.add("enableAutoSync DENIED: " + e.getMessage());
        }
    }

    // Each stat is read separately: isSyncPending/isSyncActive need
    // READ_SYNC_STATS while the others need READ_SYNC_SETTINGS, so one
    // denial must not cost the whole diagnostic line.
    private static String stat(Account account, String authority, String which) {
        try {
            if (which.equals("syncable")) {
                return String.valueOf(ContentResolver.getIsSyncable(account, authority));
            }
            if (which.equals("auto")) {
                return String.valueOf(
                        ContentResolver.getSyncAutomatically(account, authority));
            }
            if (which.equals("master")) {
                return String.valueOf(ContentResolver.getMasterSyncAutomatically());
            }
            if (which.equals("pending")) {
                return String.valueOf(ContentResolver.isSyncPending(account, authority));
            }
            return String.valueOf(ContentResolver.isSyncActive(account, authority));
        } catch (SecurityException e) {
            return "DENIED";
        } catch (RuntimeException e) {
            return "ERR:" + e.getClass().getSimpleName();
        }
    }
}
