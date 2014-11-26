package com.xcv58.joulerenergymanager;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

/**
 * Created by xcv58 on 11/25/14.
 */
public class JoulerEnergyManageDeamon extends Service {
    private final IBinder mBinder = new LocalBinder();
    public static final String JOULER_POLICY = "JoulerPolicy";
    private static final String DEFAULT_POLICY = MainActivity.DEFAULT;
    private String mChoice;
    private SharedPreferences policyPreferences;

    public class LocalBinder extends Binder {
        JoulerEnergyManageDeamon getService() {
            return JoulerEnergyManageDeamon.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(MainActivity.TAG, "Deamon Start");
        policyPreferences = getSharedPreferences(JOULER_POLICY, 0);
        mChoice = policyPreferences.getString(JOULER_POLICY, DEFAULT_POLICY);
        this.startServiceForChoice();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(MainActivity.TAG, "onBind() executed");
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(MainActivity.TAG, "Deamon stop");
    }

    public boolean isChoice(String choice) {
        return mChoice.equals(choice);
    }

    public String getChoice() {
        return mChoice;
    }

    public String putChoice(String choice) {
        Log.d(MainActivity.TAG, "Deamon Serive got new choice: " + choice + ", old is: " + mChoice);
        if (choice.equals(mChoice)) {
            return mChoice;
        }
        this.stopServiceForChoice();

        // update preference and start new service
        SharedPreferences.Editor editor = policyPreferences.edit();
        editor.putString(JOULER_POLICY, choice);
        editor.commit();
        mChoice = policyPreferences.getString(JOULER_POLICY, DEFAULT_POLICY);

        Log.d(MainActivity.TAG, "Deamon Serive update choice to: " + mChoice);
        this.startServiceForChoice();

        return mChoice;
    }

    private void stopServiceForChoice() {
        Log.d(MainActivity.TAG, "Stop service: " + mChoice);
        if (mChoice.equals(MainActivity.DEFAULT)) {
            // do nothing because default
        }
        if (mChoice.equals(MainActivity.BLACK_LIST)) {
            stopService(new Intent(getBaseContext(), JoulerEnergyManageBlackWhiteListService.class));
        }
        if (mChoice.equals(MainActivity.WHITE_LIST)) {
        }
        if (mChoice.equals(MainActivity.LIFE_TIME)) {
        }
    }

    private void startServiceForChoice() {
        Log.d(MainActivity.TAG, "Start service: " + mChoice);
        if (mChoice.equals(MainActivity.BLACK_LIST)) {
            Intent intent = new Intent(getBaseContext(), JoulerEnergyManageBlackWhiteListService.class);
            intent.putExtra(JoulerEnergyManageBlackWhiteListService.whichList, JoulerEnergyManageBlackWhiteListService.BLACK_LIST_INTENT);
            startService(intent);
        }
        if (mChoice.equals(MainActivity.WHITE_LIST)) {
            Intent intent = new Intent(getBaseContext(), JoulerEnergyManageBlackWhiteListService.class);
            intent.putExtra(JoulerEnergyManageBlackWhiteListService.whichList, JoulerEnergyManageBlackWhiteListService.WHITE_LIST_INTENT);
            startService(intent);
        }
        if (mChoice.equals(MainActivity.LIFE_TIME)) {
        }
    }
}
