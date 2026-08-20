package com.nguyenhoatien.icloudsync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;

public class IcloudAccount {

    private IcloudAccount() {
    }

    public static Account find(Context context) {
        try {
            Account[] existing = AccountManager.get(context)
                    .getAccountsByType(ContactsWriter.ACCOUNT_TYPE);
            return existing.length == 0 ? null : existing[0];
        } catch (SecurityException e) {
            SyncLog.add("find account DENIED: " + e.getMessage());
            return null;
        }
    }

    public static Account add(Context context, String appleId, String appSpecificPassword) {
        AccountManager am = AccountManager.get(context);
        Account account = new Account(appleId, ContactsWriter.ACCOUNT_TYPE);
        SyncLog.add("addAccountExplicitly type=" + ContactsWriter.ACCOUNT_TYPE);
        // The password lives in AccountManager rather than SharedPreferences so
        // it is scoped to this account type and goes away with the account.
        boolean added;
        try {
            added = am.addAccountExplicitly(account, appSpecificPassword, null);
        } catch (SecurityException e) {
            // Almost always a mismatch between ACCOUNT_TYPE and the
            // accountType in res/xml/icloud_authenticator.xml.
            SyncLog.add("addAccountExplicitly DENIED: " + e.getMessage());
            return null;
        }
        if (!added) {
            SyncLog.add("account already exists, updating password");
            Account[] existing = am.getAccountsByType(ContactsWriter.ACCOUNT_TYPE);
            for (int i = 0; i < existing.length; i++) {
                if (existing[i].name.equals(appleId)) {
                    am.setPassword(existing[i], appSpecificPassword);
                    return existing[i];
                }
            }
            return null;
        }
        IcloudSyncAdapter.enableAutoSync(account);
        return account;
    }

    public static void remove(Context context, Account account) {
        AccountManager.get(context).removeAccountExplicitly(account);
    }
}
