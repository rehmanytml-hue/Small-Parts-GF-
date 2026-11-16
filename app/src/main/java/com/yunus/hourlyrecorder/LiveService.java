package com.yunus.hourlyrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Keeps the home screen widget fresh: fetches the figures every 5 seconds until switched off. */
public class LiveService extends Service {
    static volatile boolean running = false;
    private Thread worker;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("live", "Live widget", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "live") : new Notification.Builder(this);
        Notification n = b.setSmallIcon(R.drawable.ic_stat_update).setContentTitle("Live widget on")
                .setContentText("Refreshing every 5 seconds. Turn off from the widget.").setContentIntent(open).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(7, n);
        running = true;
        worker = new Thread(new Runnable() {
            public void run() {
                while (running) {
                    long t = System.currentTimeMillis();
                    LiveWidget.draw(getApplicationContext(), LiveData.load(), true);
                    long wait = 5000 - (System.currentTimeMillis() - t);
                    if (wait > 0) try { Thread.sleep(wait); } catch (InterruptedException e) { return; }
                }
            }
        });
        worker.start();
    }

    @Override
    public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }

    @Override
    public void onDestroy() {
        running = false;
        if (worker != null) worker.interrupt();
        LiveWidget.draw(getApplicationContext(), null, false);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) { return null; }
}
