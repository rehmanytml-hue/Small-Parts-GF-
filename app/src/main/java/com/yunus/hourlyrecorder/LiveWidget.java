package com.yunus.hourlyrecorder;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Home screen widget: latest hour, pieces and efficiency. Tap LIVE to refresh every 5 seconds. */
public class LiveWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.yunus.hourlyrecorder.REFRESH";
    static final String ACTION_TOGGLE = "com.yunus.hourlyrecorder.TOGGLE_LIVE";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        draw(c, null, LiveService.running);
        refreshOnce(c);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (ACTION_REFRESH.equals(i.getAction())) refreshOnce(c);
        if (ACTION_TOGGLE.equals(i.getAction())) {
            Intent s = new Intent(c, LiveService.class);
            if (LiveService.running) c.stopService(s);
            else if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
            else c.startService(s);
            draw(c, null, !LiveService.running);
        }
    }

    static void refreshOnce(final Context c) {
        final Context app = c.getApplicationContext();
        new Thread(new Runnable() {
            public void run() { draw(app, LiveData.load(), LiveService.running); }
        }).start();
    }

    private static PendingIntent broadcast(Context c, String action, int code) {
        Intent i = new Intent(c, LiveWidget.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void draw(Context c, LiveData d, boolean live) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, LiveWidget.class));
        if (ids.length == 0) return;
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_live);
        v.setOnClickPendingIntent(R.id.w_live, broadcast(c, ACTION_TOGGLE, 1));
        v.setOnClickPendingIntent(R.id.w_refresh, broadcast(c, ACTION_REFRESH, 2));
        PendingIntent open = PendingIntent.getActivity(c, 3, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_body, open);
        v.setTextViewText(R.id.w_live, live ? "● LIVE 5s" : "○ LIVE off");
        v.setTextColor(R.id.w_live, live ? Color.parseColor("#3DDC84") : Color.parseColor("#9AA3B2"));
        if (d != null) {
            v.setTextViewText(R.id.w_title, d.section);
            String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            if (!d.ok) {
                v.setTextViewText(R.id.w_foot, "No connection · tried " + time);
            } else if (d.pieces < 0) {
                v.setTextViewText(R.id.w_hour, "No hour recorded yet");
                v.setTextViewText(R.id.w_pieces, "–");
                v.setTextViewText(R.id.w_eff, "");
                v.setTextViewText(R.id.w_prev, "");
                v.setTextViewText(R.id.w_foot, "Target " + d.target + "% · updated " + time);
            } else {
                v.setTextViewText(R.id.w_hour, "Hour " + d.hourLabel);
                v.setTextViewText(R.id.w_pieces, String.format(Locale.US, "%,d pcs", d.pieces));
                if (d.eff >= 0) {
                    v.setTextViewText(R.id.w_eff, Math.round(d.eff) + "%");
                    v.setTextColor(R.id.w_eff, d.eff + 0.0001 >= d.target ? Color.parseColor("#3DDC84") : Color.parseColor("#FF6B5E"));
                } else v.setTextViewText(R.id.w_eff, "");
                v.setTextViewText(R.id.w_prev, d.prevLine.isEmpty() ? "" : "Before: " + d.prevLine);
                v.setTextViewText(R.id.w_foot, "Today " + String.format(Locale.US, "%,d", d.total) + " pcs · target " + d.target + "% · " + time);
            }
        }
        for (int id : ids) m.updateAppWidget(id, v);
    }
}
