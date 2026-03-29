package com.example.helloworld;

import android.app.Notification;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class NotificationFilterService extends NotificationListenerService {

    private static NotificationFilterService instance;
    public static NotificationFilterService getInstance() { return instance; }

    private static final String TAG = "NotifFilter";
    public static final String PREFS_NAME = "notification_filter";
    public static final String KEY_BLOCKED_PACKAGES = "blocked_packages";
    public static final String KEY_BLOCKED_TEXT = "blocked_text_patterns";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (shouldFilter(sbn)) {
            Log.d(TAG, "Filtering notification from: " + sbn.getPackageName());
            cancelNotification(sbn.getKey());
        }
    }

    private boolean shouldFilter(StatusBarNotification sbn) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Check blocked packages
        Set<String> blockedPackages = prefs.getStringSet(KEY_BLOCKED_PACKAGES, new HashSet<String>());
        if (blockedPackages.contains(sbn.getPackageName())) {
            return true;
        }

        // Check blocked text patterns (regex against notification text)
        Set<String> blockedPatterns = prefs.getStringSet(KEY_BLOCKED_TEXT, new HashSet<String>());
        if (!blockedPatterns.isEmpty()) {
            String text = getNotificationText(sbn);
            for (String pattern : blockedPatterns) {
                try {
                    if (Pattern.compile(pattern).matcher(text).find()) {
                        return true;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Bad regex pattern: " + pattern);
                }
            }
        }

        return false;
    }

    private String getNotificationText(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        StringBuilder sb = new StringBuilder();

        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        if (title != null) sb.append(title).append(" ");

        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (text != null) sb.append(text).append(" ");

        CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (bigText != null) sb.append(bigText);

        return sb.toString();
    }

    @Override
    public void onListenerConnected() {
        instance = this;
        Log.d(TAG, "Notification listener connected");
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        Log.d(TAG, "Notification listener disconnected");
    }
}
