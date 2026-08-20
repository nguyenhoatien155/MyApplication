package com.nguyenhoatien.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.nguyenhoatien.icloudsync.IcloudSetupActivity;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView tv = new TextView(this);
        tv.setText(getString(R.string.app_name) + " — build OK");
        tv.setTextSize(20);

        // ==== icloudsync: on delete, drop this block, the import, and
        // pass tv straight to setContentView instead of root ====
        Button sync = new Button(this);
        sync.setText("iCloud Contacts");
        sync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, IcloudSetupActivity.class));
            }
        });
        // ==== end icloudsync ====

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(tv);
        root.addView(sync);

        setContentView(root);
    }
}