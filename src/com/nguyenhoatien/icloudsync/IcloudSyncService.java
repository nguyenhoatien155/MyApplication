package com.nguyenhoatien.icloudsync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class IcloudSyncService extends Service {

    // One adapter instance per process, guarded because the system may bind
    // this service from more than one thread.
    private static IcloudSyncAdapter adapter;
    private static final Object LOCK = new Object();

    @Override
    public void onCreate() {
        super.onCreate();
        synchronized (LOCK) {
            if (adapter == null) {
                adapter = new IcloudSyncAdapter(getApplicationContext(), true);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return adapter.getSyncAdapterBinder();
    }
}
