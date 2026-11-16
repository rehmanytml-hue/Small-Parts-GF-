package com.yunus.hourlyrecorder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Runs in the background, even when the app is closed, and posts a notification when a newer version is released. */
public class UpdateWorker extends Worker {

    private static final String API = "https://api.github.com/repos/rehmanytml-hue/Small-Parts-GF-/releases/latest";
    private static final String CHANNEL = "updates";
    private static final int NOTE_ID = 4201;

    public UpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        try {
            HttpURLConnection h = (HttpURLConnection) new URL(API).openConnection();
            h.setRequestProperty("Accept", "application/vnd.github+json");
            h.setRequestProperty("User-Agent", "HourlyRecorder");
            h.setConnectTimeout(10000);
            h.setReadTimeout(15000);
            if (h.getResponseCode() != 200) return Result.success();
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONObject rel = new JSONObject(sb.toString());
            int latest = Integer.parseInt(rel.getString("tag_name").replaceAll("[^0-9]", ""));
            int current = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
            NotificationManagerCompat nm = NotificationManagerCompat.from(c);
            if (latest <= current) {
                nm.cancel(NOTE_ID);
                return Result.success();
            }
            SharedPreferences sp = c.getSharedPreferences("updates", Context.MODE_PRIVATE);
            if (sp.getInt("notified", 0) >= latest) return Result.success();
            if (Build.VERSION.SDK_INT >= 33
                    && ContextCompat.checkSelfPermission(c, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return Result.success();
            }
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("Tells you when a new version of Hourly Recorder is ready");
                nm.createNotificationChannel(ch);
            }
            Intent open = new Intent(c, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_update)
                    .setContentTitle("Hourly Recorder update ready")
                    .setContentText("Version " + latest + " is available. Tap to open the app and update.")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            nm.notify(NOTE_ID, b.build());
            sp.edit().putInt("notified", latest).apply();
        } catch (Exception ignored) {
            // No connection or GitHub unreachable: try again at the next run.
        }
        return Result.success();
    }
}
