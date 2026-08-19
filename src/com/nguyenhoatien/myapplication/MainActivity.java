package com.nguyenhoatien.myapplication;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        root.addView(text("Mạng xã hội",
                android.R.style.TextAppearance_DeviceDefault_Small,
                android.R.attr.textColorSecondary));

        root.addView(text("Facebook",
                android.R.style.TextAppearance_DeviceDefault_Large,
                android.R.attr.textColorPrimary));

        root.addView(text("Minh Anh đã thích bài viết của bạn",
                android.R.style.TextAppearance_DeviceDefault_Small,
                android.R.attr.textColorSecondary));

        setContentView(root);
    }

    private TextView text(String value, int appearance, int colorAttr) {
        TextView tv = new TextView(this);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        tv.setTextAppearance(appearance);
        tv.setTextColor(themeColor(colorAttr));
        tv.setText(value);
        return tv;
    }

    private int themeColor(int attr) {
        TypedValue out = new TypedValue();
        getTheme().resolveAttribute(attr, out, true);
        return out.resourceId != 0 ? getColor(out.resourceId) : out.data;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, getResources().getDisplayMetrics());
    }
}
