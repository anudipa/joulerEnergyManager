package com.xcv58.joulerenergymanager;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.IBinder;
import android.os.JoulerPolicy;
import android.os.JoulerStats;
import android.os.JoulerStats.UidStats;
import android.os.Parcel;
import android.os.RemoteException;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;

public class LifetimeManagerService extends Service {

	final static String TAG = "LifetimeManagerService";
	final static String TAGE = "LifetimeManagerServiceError";
	final static int defaultCpuFreq = 2265600;
	static int defaultBrightness;
	
	static int soft = -1;
	static int critical = -1;
	static int lifetimeHrs = -1;
	AlarmManager alarm;
	int lastCheckedLevel = -1;
	double expectedDischargeRate = 0.0; //level per ms
	boolean screen = true;
	boolean changeCpuFreq = false;
	boolean changeBrightness = false;
	boolean doRateLimitFG = false ;
	boolean doRateLimitBG = false;
	boolean doResetPriority = false;
	//boolean toggle = false;
	static int cpufreq = defaultCpuFreq;
	static int brightness = 0;
	static int priority = 10;
	static JoulerPolicy knob ;
	static JoulerStats stats;
	List<Integer> rateLimitedUids = new ArrayList<>();
	
	BroadcastReceiver onBatteryChange = new BroadcastReceiver(){

		@Override
		public void onReceive(Context context, Intent intent) {
			int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
			int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
			boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;
			try {
				JSONObject json = new JSONObject();
				json.put("currentBatteryLevel", level);
				json.put("isCharging", isCharging);
				Log.i(TAG, json.toString());
			}catch (JSONException e) {
				// TODO Auto-generated catch block
				Log.i(TAGE, "Error @ onBatteryChange receiver: "+e.getMessage());
			}
			
			
			if(isCharging) {
				if( level == 100)
					lastCheckedLevel = -1;
				resetPolicy();
				
			}
			else {
					if(level != lastCheckedLevel) {
						//Log.i(TAG, "battery level="+level+"lastCheckedLevel="+lastCheckedLevel);
						if(level <= soft && level > critical) {
							if((lastCheckedLevel== -1) || (lastCheckedLevel > level && (lastCheckedLevel - level) >= 5)) {	
								//loading statistics
								load();
								lastCheckedLevel = level;
								boolean state = willLifeEndSoon(level);			//logs info
								
								if(!state) {
									resetPolicy();					//logs info
									return;
								}
								
								int diff = (soft - (soft-critical)/2);
								Log.i(TAG,"check level: level = "+level+" range > "+diff);
									
								if(level > (soft - (soft-critical)/2)) {
									if (defaultBrightness > 45){
										brightness = 2 * (defaultBrightness / 3);
										changeBrightness = true;
									}
								}else {
									if (defaultBrightness > 45)
										brightness = defaultBrightness /3 ;
									else
										brightness = 15;
									
									changeBrightness = true;	
									cpufreq = 1574400;
									changeCpuFreq = true;
								}
								
								
								if(screen) {
									screenOnPolicies();			//logs info
								}else {
									screenOffPolicies();			//logs info
								}
							}
									
						}else if(level <= critical) {
							if(lastCheckedLevel== -1 || (lastCheckedLevel > level && (lastCheckedLevel - level) >= 2)) {
								//loading statistics
								load();
								boolean state = willLifeEndSoon(level);		//logs info
								lastCheckedLevel = level;
								if(!state) {
									resetPolicy();				//logs info
									return;
								}
								brightness= 15;
								cpufreq = 1190400;
								priority = 15;
								changeBrightness = true;
								changeCpuFreq = true;
								doRateLimitBG = true;
								doResetPriority = true;
								
								
								if(screen) {
									screenOnPolicies();			//logs info
								}else {
									screenOffPolicies();			//logs info
								}
							}
						}
					}
				}
				
			}
	
	};
	
	BroadcastReceiver screenReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
				load();
				screen = false;
				screenOffPolicies();								//logs info
			}else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
				load();
				screen = true;
				screenOnPolicies();								//logs info
			}
		}
	};
	
	private void resetPolicy() {
		changeBrightness = false;
		changeCpuFreq = false;
		doRateLimitBG = false;
		doRateLimitFG = false;
		doResetPriority = false;
		
		brightness = defaultBrightness;
		cpufreq = defaultCpuFreq;
		priority = 10;
		setBrightness();
		knob.controlCpuMaxFrequency(defaultCpuFreq);
		if(rateLimitedUids.size() > 0 )
			resetRateLimit();
		printLog();
		
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		soft = intent.getIntExtra("soft_threshold", -1);
		critical = intent.getIntExtra("critical_threshold", -1);
		lifetimeHrs = intent.getIntExtra("lifetime", -1);
		if(soft == -1 || critical == -1 || lifetimeHrs == -1)
			setDefault();
		knob = (JoulerPolicy)getSystemService(Context.JOULER_SERVICE);
		try {
			defaultBrightness = android.provider.Settings.System.getInt(
			        getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS);
			} catch (SettingNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		brightness = defaultBrightness;
		setExpectedRate();
		
		try {
			JSONObject json = new JSONObject();
			json.put("expectedDischargeRate", expectedDischargeRate);
			Log.i(TAG, json.toString());
		}catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		IntentFilter intent1 = new IntentFilter();
		intent1.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(onBatteryChange,intent1);
        IntentFilter intent2 = new IntentFilter();
		intent2.addAction(Intent.ACTION_SCREEN_ON);
		intent2.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver(screenReceiver,intent2);
		
		printLog();
		
		return startId;
		
	}
	
	private void setExpectedRate() {
		expectedDischargeRate = (double)100 / (double)(lifetimeHrs * 60 * 60 * 1000);
		
	}

	@Override
	public void onDestroy(){
		resetPolicy();
		unregisterReceiver(screenReceiver);
		unregisterReceiver(onBatteryChange);
		
	}
	
	private void setDefault() {
		soft = 75;
		critical = 25;
		lifetimeHrs = 15;
	}
	
	
	
	private void load(){
		try {
			byte[] data = knob.getStatistics();
			if (data != null) {
				//Log.i(TAG, "So knob got me the stats");
				Parcel parcel = Parcel.obtain();
				parcel.unmarshall(data, 0, data.length);
				parcel.setDataPosition(0);
				stats = JoulerStats.CREATOR.createFromParcel(parcel);
				//Log.i(TAG,"Loading Statistics");
			}
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	private void screenOffPolicies(){
		printLog();
		if(doRateLimitBG)
			setRateLimit();
	
	}
	

	private void screenOnPolicies(){
		printLog();
		
		if(changeBrightness)
			setBrightness();
		if(changeCpuFreq)
			setCpuFreq();
		if(doRateLimitBG)
			resetRateLimit();
		if(doResetPriority)
			setPriority();
		
	}
	
	private void setPriority() {
		if(stats == null || stats.mUidArray.size() == 0)
			return;
		synchronized (stats) {
			
		/*	if(lastCheckedLevel < soft && lastCheckedLevel > critical)
				priority = 15;
			else if(lastCheckedLevel <  critical)
				priority = 19;
		*/	
			try {
				for(int i=0; i< stats.mUidArray.size(); i++) {
					UidStats u = stats.mUidArray.valueAt(i);
					if(u.getUid() < 10000)
						continue;
					JSONObject js = new JSONObject();
					js.put("uid", u.getUid());
					js.put("packagename", u.packageName);
					js.put("realPriority", knob.getPriority(u.getUid()));
					
					if(u.getState() == false && knob.getPriority(u.getUid()) > 0) {
						knob.resetPriority(u.getUid(), priority);
						js.put("changedPriority", knob.getPriority(u.getUid()));
					}
					Log.i(TAG,js.toString());
				}
			}catch(JSONException e){
				Log.i(TAGE,"Error @ setpriority: "+e.getMessage());
			}
		}
	}
	
	
	private void setRateLimit() {
		if(stats == null || stats.mUidArray.size() == 0)
			return;
		Log.i(TAG, "Set Rate Limit");
		synchronized(stats) {
			try{
				for(int i=0; i< stats.mUidArray.size(); i++) {
					UidStats u = stats.mUidArray.valueAt(i);
					JSONObject js = new JSONObject();
					
					if(u.getState() == false && u.getAudioEnergy() == 0.0 && u.getWifiDataEnergy() > 0.0 && u.getThrottle() == false) {
						rateLimitedUids.add(u.getUid());
						knob.rateLimitForUid(u.getUid());
						js.put("uid", u.getUid());
						js.put("packageName", u.packageName);
						js.put("rateLimit", true);
						js.put("wifiDataEnergy", u.getWifiDataEnergy());
					}
				}
			}catch(JSONException e) {
				Log.i(TAGE,"Error @ setRateLimit: "+e.getMessage());
			}
		}
		
	}
	
	private void resetRateLimit() {
		if(rateLimitedUids.size() == 0)
			return;
		try{
		for(int i=0; i< rateLimitedUids.size(); i++) {
			int uid = rateLimitedUids.get(i);
			UidStats u = stats.mUidArray.get(uid);
			JSONObject js = new JSONObject();
			
			if(u.getThrottle()==true) {
				knob.rateLimitForUid(uid);
				js.put("uid", u.getUid());
				js.put("packageName", u.packageName);
				js.put("rateLimit", false);
				js.put("wifiDataEnergy", u.getWifiDataEnergy());
			}
			
		}
		}catch(JSONException e){
			Log.i(TAGE, "Error @ resetRateLimit: "+e.getMessage());
		}
		rateLimitedUids.clear();
	}

	private void setCpuFreq() {
		knob.controlCpuMaxFrequency(cpufreq);
		
	}

	private void setBrightness() {
		android.provider.Settings.System.putInt(getContentResolver(),
                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                brightness);
        android.provider.Settings.System.putInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
		
	}


	boolean willLifeEndSoon(int level) {
		//load();
		if(stats == null || stats.mSystemStats == null) {
			Log.i(TAG, "NULL Stats");
			return false;
		}
		double currDischargeRate = stats.mSystemStats.getCurrentDischargeRate(); 
		long expectedTimeLeft = (long) (level/expectedDischargeRate);
		long actualTimeLeft = (long) (level/currDischargeRate);
		
		try {
			JSONObject json = new JSONObject();
			json.put("actualTimeLeft", actualTimeLeft);
			json.put("exepectedTimeLeft", expectedTimeLeft);
			json.put("currentDischargeRate", currDischargeRate);
			json.put("expectedDischargeRate", expectedDischargeRate);
			json.put("uptime", stats.mSystemStats.getUptime());
			Log.i(TAG, json.toString());
		}catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if ( actualTimeLeft < (expectedTimeLeft + 600000) ) {
			if (actualTimeLeft < (lifetimeHrs*60*60*1000)/3) {			//remember to get rid of 10
				
				double hrs = Math.ceil(((double)actualTimeLeft / 3600000.0)); 
				Log.i(TAG,"hrs left="+ hrs);
				Notification mBuilder =
				        new Notification.Builder(this)
				        .setContentTitle("Reduce device usage")
				        .setContentText("Battery will run out in next "+hrs+" hours")
				        .setSmallIcon(R.drawable.ic_launcher)
				        .setAutoCancel(true).build();
				NotificationManager mNotificationManager = (NotificationManager) 
						  getSystemService(NOTIFICATION_SERVICE); 
				mNotificationManager.notify(0,mBuilder);
				
			}
			stats = null;
			return true;
		}
		stats = null;
		return false;
	}
	
	void printLog() {
		
		try {
			JSONObject json = new JSONObject();
			json.put("changeCpuFreq", changeCpuFreq);
			json.put("changeBrightness", changeBrightness);
			json.put("doRateLimitBG", doRateLimitBG);
			json.put("doResetPriority", doResetPriority);
			json.put("screen_state", screen);
			json.put("cpufreq", cpufreq);
			json.put("brightness", brightness);
			json.put("rateLimitUid", rateLimitedUids);
			json.put("batteryLevel", lastCheckedLevel);
			json.put("priority", priority);
			Log.i(TAG, json.toString());
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	

}
