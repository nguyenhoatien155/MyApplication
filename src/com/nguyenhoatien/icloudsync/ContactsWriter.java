package com.nguyenhoatien.icloudsync;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import java.util.ArrayList;
import java.util.List;

public class ContactsWriter {

    public static final String ACCOUNT_TYPE = "com.nguyenhoatien.icloudsync";

    private static final int MAX_OPS_PER_BATCH = 400;

    public static class Result {
        public int deleted;
        public int inserted;
        public int skipped;
    }

    private final ContentResolver resolver;

    public ContactsWriter(ContentResolver resolver) {
        this.resolver = resolver;
    }

    public Result replaceAll(String accountName, List<VCardContact> contacts)
            throws RemoteException, OperationApplicationException {
        Result result = new Result();
        if (contacts == null || contacts.isEmpty()) {
            return result;
        }

        result.deleted = deleteAll(accountName);
        SyncLog.add("deleted " + result.deleted + " old raw contacts of " + accountName);

        List<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        for (int i = 0; i < contacts.size(); i++) {
            VCardContact c = contacts.get(i);
            int before = ops.size();
            appendContact(ops, accountName, c);
            if (ops.size() == before) {
                result.skipped++;
                continue;
            }
            if (ops.size() >= MAX_OPS_PER_BATCH) {
                apply(ops);
                result.inserted += countRaws(ops);
                ops.clear();
            }
        }
        if (!ops.isEmpty()) {
            apply(ops);
            result.inserted += countRaws(ops);
        }
        return result;
    }

    private void appendContact(List<ContentProviderOperation> ops,
            String accountName, VCardContact c) {
        if (isBlank(c.displayName) && isBlank(c.given) && isBlank(c.family)
                && c.phones.isEmpty() && c.emails.isEmpty()) {
            return;
        }

        int backRef = ops.size();

        ops.add(ContentProviderOperation.newInsert(syncUri(RawContacts.CONTENT_URI))
                .withValue(RawContacts.ACCOUNT_NAME, accountName)
                .withValue(RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
                .withValue(RawContacts.SOURCE_ID, c.uid)
                .withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DEFAULT)
                .build());

        if (!isBlank(c.displayName) || !isBlank(c.given) || !isBlank(c.family)) {
            ops.add(data(backRef, StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.DISPLAY_NAME, nullIfBlank(c.displayName))
                    .withValue(StructuredName.GIVEN_NAME, nullIfBlank(c.given))
                    .withValue(StructuredName.FAMILY_NAME, nullIfBlank(c.family))
                    .withValue(StructuredName.MIDDLE_NAME, nullIfBlank(c.middle))
                    .withValue(StructuredName.PREFIX, nullIfBlank(c.prefix))
                    .withValue(StructuredName.SUFFIX, nullIfBlank(c.suffix))
                    .build());
        }

        for (int i = 0; i < c.phones.size(); i++) {
            VCardContact.Typed p = c.phones.get(i);
            if (isBlank(p.value)) {
                continue;
            }
            ops.add(data(backRef, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, p.value)
                    .withValue(Phone.TYPE, phoneType(p.type))
                    .withValue(Phone.LABEL, phoneLabel(p.type))
                    .build());
        }

        for (int i = 0; i < c.emails.size(); i++) {
            VCardContact.Typed e = c.emails.get(i);
            if (isBlank(e.value)) {
                continue;
            }
            ops.add(data(backRef, Email.CONTENT_ITEM_TYPE)
                    .withValue(Email.ADDRESS, e.value)
                    .withValue(Email.TYPE, emailType(e.type))
                    .withValue(Email.LABEL, emailLabel(e.type))
                    .build());
        }
    }

    private static ContentProviderOperation.Builder data(int backRef, String mimeType) {
        return ContentProviderOperation.newInsert(syncUri(Data.CONTENT_URI))
                .withValueBackReference(Data.RAW_CONTACT_ID, backRef)
                .withValue(Data.MIMETYPE, mimeType);
    }

    private int deleteAll(String accountName) {
        Uri uri = syncUri(RawContacts.CONTENT_URI);
        return resolver.delete(uri,
                RawContacts.ACCOUNT_NAME + "=? AND " + RawContacts.ACCOUNT_TYPE + "=?",
                new String[] {accountName, ACCOUNT_TYPE});
    }

    private void apply(List<ContentProviderOperation> ops)
            throws RemoteException, OperationApplicationException {
        SyncLog.add("applyBatch " + ops.size() + " ops");
        android.content.ContentProviderResult[] r = resolver.applyBatch(
                ContactsContract.AUTHORITY, new ArrayList<ContentProviderOperation>(ops));
        SyncLog.add("  -> " + r.length + " results, first uri=" + (r.length > 0 ? r[0].uri : null));
    }

    private static int countRaws(List<ContentProviderOperation> ops) {
        int n = 0;
        for (int i = 0; i < ops.size(); i++) {
            if (ops.get(i).getUri().getPath().endsWith("/raw_contacts")) {
                n++;
            }
        }
        return n;
    }

    static Uri syncUri(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .build();
    }

    static int phoneType(String type) {
        if (type == null) {
            return Phone.TYPE_OTHER;
        }
        if (type.equalsIgnoreCase("CELL") || type.equalsIgnoreCase("IPHONE")) {
            return Phone.TYPE_MOBILE;
        }
        if (type.equalsIgnoreCase("HOME")) {
            return Phone.TYPE_HOME;
        }
        if (type.equalsIgnoreCase("WORK")) {
            return Phone.TYPE_WORK;
        }
        if (type.equalsIgnoreCase("FAX") || type.equalsIgnoreCase("HOMEFAX")) {
            return Phone.TYPE_FAX_HOME;
        }
        if (type.equalsIgnoreCase("WORKFAX")) {
            return Phone.TYPE_FAX_WORK;
        }
        if (type.equalsIgnoreCase("PAGER")) {
            return Phone.TYPE_PAGER;
        }
        if (type.equalsIgnoreCase("MAIN")) {
            return Phone.TYPE_MAIN;
        }
        return Phone.TYPE_CUSTOM;
    }

    static String phoneLabel(String type) {
        return phoneType(type) == Phone.TYPE_CUSTOM ? type : null;
    }

    static int emailType(String type) {
        if (type == null) {
            return Email.TYPE_OTHER;
        }
        if (type.equalsIgnoreCase("HOME")) {
            return Email.TYPE_HOME;
        }
        if (type.equalsIgnoreCase("WORK")) {
            return Email.TYPE_WORK;
        }
        if (type.equalsIgnoreCase("MOBILE")) {
            return Email.TYPE_MOBILE;
        }
        return Email.TYPE_CUSTOM;
    }

    static String emailLabel(String type) {
        return emailType(type) == Email.TYPE_CUSTOM ? type : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().length() == 0;
    }

    private static String nullIfBlank(String s) {
        return isBlank(s) ? null : s;
    }
}
