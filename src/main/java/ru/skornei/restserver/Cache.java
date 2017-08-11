package ru.skornei.restserver;

import android.app.Application;
import android.content.Context;

public final class Cache {

    private static Application context;

    private Cache() {
        throw new RuntimeException();
    }

    public static synchronized void initialize(Application application) {
        context = application;
    }

    public static Context getContext() {
        return context;
    }
}
