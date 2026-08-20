package com.nguyenhoatien.icloudsync;

import android.Manifest;
import android.accounts.Account;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class IcloudSetupActivity extends Activity {

    private static final int REQ_CONTACTS = 1;

    private EditText appleId;
    private EditText password;
    private TextView status;
    private TextView logView;
    private ScrollView logScroll;
    private Handler handler;
    private Runnable tick;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        appleId = new EditText(this);
        appleId.setHint("Apple ID");
        appleId.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        root.addView(appleId, wide());

        password = new EditText(this);
        password.setHint("App-specific password");
        password.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(password, wide());

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(button("Save", new Runnable() {
            @Override
            public void run() {
                saveAccount();
            }
        }), even());
        buttons.addView(button("Sync", new Runnable() {
            @Override
            public void run() {
                syncNow();
            }
        }), even());
        buttons.addView(button("Count", new Runnable() {
            @Override
            public void run() {
                countContacts();
            }
        }), even());
        buttons.addView(button("Clear", new Runnable() {
            @Override
            public void run() {
                SyncLog.clear();
                refreshLog();
            }
        }), even());
        root.addView(buttons, wide());

        status = new TextView(this);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(status, wide());

        logView = new TextView(this);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(10);
        logView.setTextIsSelectable(true);
        logScroll = new ScrollView(this);
        logScroll.setBackgroundColor(Color.parseColor("#11000000"));
        logScroll.addView(logView);
        // Weight 1 with height 0 makes the log take every pixel the buttons
        // and fields leave over, however tall the screen is.
        root.addView(logScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        handler = new Handler(Looper.getMainLooper());
        tick = new Runnable() {
            @Override
            public void run() {
                refreshLog();
                handler.postDelayed(this, 1000);
            }
        };

        showAccount();

        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            SyncLog.add("requesting contacts permission");
            requestPermissions(new String[] {
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS,
            }, REQ_CONTACTS);
        } else {
            SyncLog.add("contacts permission already granted");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] granted) {
        for (int i = 0; i < perms.length; i++) {
            SyncLog.add("permission " + perms[i] + " granted="
                    + (granted[i] == PackageManager.PERMISSION_GRANTED));
        }
    }

    private void showAccount() {
        Account existing = IcloudAccount.find(this);
        if (existing == null) {
            status.setText("No account yet");
            return;
        }
        appleId.setText(existing.name);
        status.setText("Account: " + existing.name);
    }

    private void saveAccount() {
        String id = appleId.getText().toString().trim();
        String pw = password.getText().toString().trim();
        if (id.length() == 0 || pw.length() == 0) {
            status.setText("Enter both fields");
            return;
        }
        SyncLog.add("saving account " + id + ", password length " + pw.length());
        Account account = IcloudAccount.add(this, id, pw);
        if (account == null) {
            status.setText("Could not add account");
            SyncLog.add("addAccountExplicitly returned false and no match found");
            return;
        }
        // The password is now in AccountManager, so drop the on-screen copy.
        password.setText("");
        status.setText("Saved " + account.name);
        refreshLog();
    }

    private void syncNow() {
        Account account = IcloudAccount.find(this);
        if (account == null) {
            status.setText("Save an account first");
            return;
        }
        IcloudSyncAdapter.requestSync(account);
        status.setText("Sync requested");
        refreshLog();
    }

    private void countContacts() {
        Account account = IcloudAccount.find(this);
        if (account == null) {
            SyncLog.add("count: no account");
            return;
        }
        Cursor c = null;
        try {
            c = getContentResolver().query(RawContacts.CONTENT_URI,
                    new String[] {RawContacts._ID, RawContacts.SOURCE_ID, RawContacts.DELETED},
                    RawContacts.ACCOUNT_TYPE + "=?",
                    new String[] {ContactsWriter.ACCOUNT_TYPE}, null);
            if (c == null) {
                SyncLog.add("count: query returned null cursor");
                return;
            }
            int deleted = 0;
            while (c.moveToNext()) {
                if (c.getInt(2) != 0) {
                    deleted++;
                }
            }
            SyncLog.add("count: " + c.getCount() + " raw contacts for this account type, "
                    + deleted + " flagged DELETED");
        } catch (Exception e) {
            SyncLog.add("count failed", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }

        Cursor all = null;
        try {
            all = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                    new String[] {ContactsContract.Contacts._ID}, null, null, null);
            SyncLog.add("count: " + (all == null ? -1 : all.getCount())
                    + " aggregated contacts on device");
        } catch (Exception e) {
            SyncLog.add("count all failed", e);
        } finally {
            if (all != null) {
                all.close();
            }
        }
        refreshLog();
    }

    private void refreshLog() {
        String text = SyncLog.dump();
        if (text.equals(logView.getText().toString())) {
            return;
        }
        logView.setText(text);
        logScroll.post(new Runnable() {
            @Override
            public void run() {
                logScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private Button button(String label, final Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        return b;
    }

    private static LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams even() {
        return new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }
}
