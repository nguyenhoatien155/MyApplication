package com.nguyenhoatien.icloudsync;

import android.Manifest;
import android.accounts.Account;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class IcloudSetupActivity extends Activity {

    private static final int REQ_CONTACTS = 1;

    private EditText appleId;
    private EditText password;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
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

        Button save = new Button(this);
        save.setText("Save account");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAccount();
            }
        });
        root.addView(save, wide());

        Button sync = new Button(this);
        sync.setText("Sync now");
        sync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                syncNow();
            }
        });
        root.addView(sync, wide());

        status = new TextView(this);
        root.addView(status, wide());

        setContentView(root);

        Account existing = IcloudAccount.find(this);
        if (existing != null) {
            appleId.setText(existing.name);
            status.setText("Account: " + existing.name);
        }

        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS,
            }, REQ_CONTACTS);
        }
    }

    private void saveAccount() {
        String id = appleId.getText().toString().trim();
        String pw = password.getText().toString().trim();
        if (id.length() == 0 || pw.length() == 0) {
            status.setText("Enter both fields");
            return;
        }
        Account account = IcloudAccount.add(this, id, pw);
        if (account == null) {
            status.setText("Could not add account");
            return;
        }
        // The password is now in AccountManager, so drop the on-screen copy.
        password.setText("");
        status.setText("Saved " + account.name + ", syncing hourly");
    }

    private void syncNow() {
        Account account = IcloudAccount.find(this);
        if (account == null) {
            status.setText("Save an account first");
            return;
        }
        IcloudSyncAdapter.requestSync(account);
        status.setText("Sync requested, watch logcat -s IcloudSync");
    }

    private static LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
