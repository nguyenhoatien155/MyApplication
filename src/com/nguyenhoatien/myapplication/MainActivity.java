package com.nguyenhoatien.myapplication;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView tv = new TextView(this);
        tv.setText(getString(R.string.app_name) + " — build OK");
        tv.setTextSize(20);

        setContentView(tv);
    }
}