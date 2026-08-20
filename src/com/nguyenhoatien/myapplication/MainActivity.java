package com.nguyenhoatien.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.nguyenhoatien.icloudsync.CrashLog;
import com.nguyenhoatien.icloudsync.IcloudSetupActivity;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ==== feature:icloudsync ====
        CrashLog.install(this);
        // ==== /feature:icloudsync ====

        TextView tv = new TextView(this);
        tv.setText(getString(R.string.app_name) + " — build OK");
        tv.setTextSize(20);

        setContentView(tv);

        // ==== feature:icloudsync ====
        Button sync = new Button(this);
        sync.setText(CrashLog.read(this) == null
                ? "iCloud Contacts" : "iCloud Contacts (crash logged)");
        sync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, IcloudSetupActivity.class));
            }
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(sync);
        addContentView(root, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        // ==== /feature:icloudsync ====
    }
}