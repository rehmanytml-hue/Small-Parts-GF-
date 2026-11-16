package com.yunus.hourlyrecorder;

import android.app.Application;

/** Starts push messaging as soon as the process starts, including when a push wakes the app. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FcmSetup.init(this);
    }
}
