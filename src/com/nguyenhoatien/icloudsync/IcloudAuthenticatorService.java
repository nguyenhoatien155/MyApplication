package com.nguyenhoatien.icloudsync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class IcloudAuthenticatorService extends Service {

    private IcloudAuthenticator authenticator;

    @Override
    public void onCreate() {
        super.onCreate();
        authenticator = new IcloudAuthenticator(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return authenticator.getIBinder();
    }
}
