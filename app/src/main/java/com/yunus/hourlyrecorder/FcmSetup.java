package com.yunus.hourlyrecorder;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

/** Starts Firebase Cloud Messaging so the phone can receive the instant "new version" push. */
final class FcmSetup {
    static final String TOPIC = "updates";

    private FcmSetup() {
    }

    /** Returns true when push is set up and this phone is subscribed. */
    static boolean init(Context context) {
        try {
            Context c = context.getApplicationContext();
            String appId = c.getString(R.string.fcm_app_id);
            if (appId == null || appId.contains("PASTE") || !appId.contains(":android:")) return false;
            if (FirebaseApp.getApps(c).isEmpty()) {
                FirebaseOptions options = new FirebaseOptions.Builder()
                        .setApplicationId(appId)
                        .setApiKey(c.getString(R.string.fcm_api_key))
                        .setProjectId(c.getString(R.string.fcm_project_id))
                        .setGcmSenderId(c.getString(R.string.fcm_sender_id))
                        .build();
                FirebaseApp.initializeApp(c, options);
            }
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
