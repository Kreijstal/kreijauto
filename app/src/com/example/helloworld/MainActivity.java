package com.example.helloworld;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private LinearLayout listContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(NotificationFilterService.PREFS_NAME, MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        // Title
        TextView title = new TextView(this);
        title.setText("Notification Filter");
        title.setTextSize(24);
        root.addView(title);

        // Status / enable button
        Button enableBtn = new Button(this);
        enableBtn.setText("Grant Notification Access");
        enableBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
        });
        root.addView(enableBtn);

        // Section: Block by package
        root.addView(sectionHeader("Block by Package Name"));

        final EditText pkgInput = new EditText(this);
        pkgInput.setHint("e.g. com.whatsapp");
        pkgInput.setSingleLine(true);
        root.addView(pkgInput);

        Button addPkg = new Button(this);
        addPkg.setText("Add Package");
        addPkg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pkg = pkgInput.getText().toString().trim();
                if (!pkg.isEmpty()) {
                    addToSet(NotificationFilterService.KEY_BLOCKED_PACKAGES, pkg);
                    pkgInput.setText("");
                    refreshList();
                }
            }
        });
        root.addView(addPkg);

        // Section: Block by text pattern
        root.addView(sectionHeader("Block by Text (Regex)"));

        final EditText textInput = new EditText(this);
        textInput.setHint("e.g. (?i)spam|promo");
        textInput.setSingleLine(true);
        root.addView(textInput);

        Button addText = new Button(this);
        addText.setText("Add Pattern");
        addText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pattern = textInput.getText().toString().trim();
                if (!pattern.isEmpty()) {
                    addToSet(NotificationFilterService.KEY_BLOCKED_TEXT, pattern);
                    textInput.setText("");
                    refreshList();
                }
            }
        });
        root.addView(addText);

        // Current rules list
        root.addView(sectionHeader("Active Rules"));

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        scroll.addView(root);
        setContentView(scroll);

        refreshList();
    }

    private void refreshList() {
        listContainer.removeAllViews();

        Set<String> packages = prefs.getStringSet(
                NotificationFilterService.KEY_BLOCKED_PACKAGES, new HashSet<String>());
        for (final String pkg : packages) {
            listContainer.addView(ruleRow("PKG: " + pkg,
                    NotificationFilterService.KEY_BLOCKED_PACKAGES, pkg));
        }

        Set<String> patterns = prefs.getStringSet(
                NotificationFilterService.KEY_BLOCKED_TEXT, new HashSet<String>());
        for (final String pattern : patterns) {
            listContainer.addView(ruleRow("TXT: " + pattern,
                    NotificationFilterService.KEY_BLOCKED_TEXT, pattern));
        }

        if (packages.isEmpty() && patterns.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No rules configured");
            listContainer.addView(empty);
        }
    }

    private View ruleRow(String label, final String key, final String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView text = new TextView(this);
        text.setText(label);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text);

        Button del = new Button(this);
        del.setText("X");
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeFromSet(key, value);
                refreshList();
            }
        });
        row.addView(del);

        return row;
    }

    private void addToSet(String key, String value) {
        Set<String> set = new HashSet<>(prefs.getStringSet(key, new HashSet<String>()));
        set.add(value);
        prefs.edit().putStringSet(key, set).apply();
        Toast.makeText(this, "Added: " + value, Toast.LENGTH_SHORT).show();
    }

    private void removeFromSet(String key, String value) {
        Set<String> set = new HashSet<>(prefs.getStringSet(key, new HashSet<String>()));
        set.remove(value);
        prefs.edit().putStringSet(key, set).apply();
    }

    private TextView sectionHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(18);
        tv.setPadding(0, dp(16), 0, dp(4));
        return tv;
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
