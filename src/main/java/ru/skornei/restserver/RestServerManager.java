package ru.skornei.restserver;

import android.app.Application;

public final class RestServerManager {

    private RestServerManager() {
        throw new RuntimeException();
    }

    public static void initialize(Application application) {
        Cache.initialize(application);
    }
}
