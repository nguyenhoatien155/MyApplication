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
        String password = accounts.getPassword(account);
        if (password == null || password.length() == 0) {
            Log.w(TAG, "no password stored for " + account.name);
            syncResult.stats.numAuthExceptions++;
            return;
        }

        try {
            CardDavClient client = new CardDavClient(
                    CardDavClient.DEFAULT_ROOT, account.name, password);
            List<VCardContact> contacts = client.fetchContacts();
            Log.i(TAG, "fetched " + contacts.size() + " contacts");

            ContactsWriter writer = new ContactsWriter(
                    getContext().getContentResolver());
            ContactsWriter.Result r = writer.replaceAll(account.name, contacts);
            Log.i(TAG, "deleted " + r.deleted + ", inserted " + r.inserted
                    + ", skipped " + r.skipped);

            if (contacts.isEmpty()) {
                // Nothing was written, so report it as a soft error rather than
                // a clean sync: an empty answer is far more likely a server
                // hiccup than a genuinely emptied address book.
                syncResult.stats.numIoExceptions++;
            }
        } catch (CardDavClient.DavException e) {
            Log.e(TAG, "dav error " + e.status + ": " + e.getMessage());
            if (e.isAuthFailure()) {
                syncResult.stats.numAuthExceptions++;
            } else {
                syncResult.stats.numIoExceptions++;
            }
        } catch (IOException e) {
            Log.e(TAG, "network error", e);
            syncResult.stats.numIoExceptions++;
        } catch (Exception e) {
            // A parse or provider failure would otherwise kill the sync thread
            // and be retried forever, so mark it hard and let it drop.
            Log.e(TAG, "sync failed", e);
            syncResult.databaseError = true;
        }
    }

    public static void requestSync(Account account) {
        Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        ContentResolver.requestSync(account, android.provider.ContactsContract.AUTHORITY, extras);
    }

    public static void enableAutoSync(Account account) {
        String authority = android.provider.ContactsContract.AUTHORITY;
        ContentResolver.setIsSyncable(account, authority, 1);
        ContentResolver.setSyncAutomatically(account, authority, true);
        ContentResolver.addPeriodicSync(account, authority, new Bundle(), 3600L);
    }
}
