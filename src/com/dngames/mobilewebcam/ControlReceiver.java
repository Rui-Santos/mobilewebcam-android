package com.dngames.mobilewebcam;

import java.util.Calendar;
import java.util.concurrent.atomic.AtomicInteger;

import com.dngames.mobilewebcam.PhotoSettings.Mode;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

public class ControlReceiver extends BroadcastReceiver
{
	public static AtomicInteger PhotoCount = new AtomicInteger(0);
	public static long LastEventTime = 0;

	public static synchronized boolean takePicture()
	{
		if(PhotoCount.getAndDecrement() > 0)
			return true;
		
		return false;
	}
	
    @Override
    public void onReceive(Context context, Intent intent)
    {
		SharedPreferences prefs = context.getSharedPreferences(MobileWebCam.SHARED_PREFS_NAME, 0);
		if(intent.getAction().equals("com.dngames.mobilewebcam.START"))
		{
			Start(context, prefs);
		}
		else if(intent.getAction().equals("com.dngames.mobilewebcam.STOP"))
		{
			Stop(context, prefs);
		}
		else if(intent.getAction().equals("com.dngames.mobilewebcam.PHOTO"))
		{
			int default_cnt = PhotoSettings.getEditInt(context, prefs, "cam_intents_repeat", 1);
			int cnt = default_cnt;
			if(intent.hasExtra("count"))
			{
				Bundle extras = intent.getExtras();
				Object val = extras.get("count");
				try
				{
					cnt = Integer.parseInt(val.toString());
				}
				catch(NumberFormatException e)
				{
					e.printStackTrace();
					MobileWebCam.LogE("Error: com.dngames.mobilewebcam.PHOTO intent sent with wrong extra int 'count'!");
				}
			}

			EventPhoto(context, prefs, cnt);
		}
    }
    
    public static void EventPhoto(Context context, SharedPreferences prefs, int cnt)
    {
		long curtime = System.currentTimeMillis();

		int triggerpause = PhotoSettings.getEditInt(context, prefs, "eventtrigger_pausetime", 0) * 1000;
		if(triggerpause > 0 && (curtime - LastEventTime < triggerpause))
		{
			MobileWebCam.LogI("Skipped trigger event because only " + triggerpause + " ms gone!");
			return;
		}
		
		LastEventTime = curtime;

		PhotoCount.set(cnt);
		
		PhotoSettings.Mode mode = PhotoSettings.getCamMode(prefs);
		if(mode == Mode.HIDDEN || mode == Mode.BACKGROUND)
		{
			AlarmManager alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
			Intent i = new Intent(context, PhotoAlarmReceiver.class);
			PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, i, 0);
			alarmMgr.cancel(pendingIntent);
			Calendar time = Calendar.getInstance();
			time.setTimeInMillis(System.currentTimeMillis());
			time.add(Calendar.SECOND, 0);
			alarmMgr.set(AlarmManager.RTC_WAKEUP, time.getTimeInMillis(), pendingIntent);
		}
		else
		{
			Intent i = new Intent(context, MobileWebCam.class);
			i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			i.putExtra("command", "photo");
			context.startActivity(i);
		}
    }

	public static void Start(Context context, SharedPreferences prefs)
	{
		SharedPreferences.Editor edit = prefs.edit();
		edit.putBoolean("mobilewebcam_enabled", true);
		edit.commit();

		switch(PhotoSettings.getCamMode(prefs))
		{
		case HIDDEN:
		case BACKGROUND:
			{
				AlarmManager alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
				Intent i = new Intent(context, PhotoAlarmReceiver.class);
				PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, i, 0);
				alarmMgr.cancel(pendingIntent);
				Calendar time = Calendar.getInstance();
				time.setTimeInMillis(System.currentTimeMillis());
				time.add(Calendar.SECOND, 1);
				alarmMgr.set(AlarmManager.RTC_WAKEUP, time.getTimeInMillis(), pendingIntent);
			}
			break;
		case MANUAL:
		case NORMAL:
		default:
			Intent i = new Intent(context, MobileWebCam.class);
			i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			i.putExtra("command", "start");
			context.startActivity(i);
			break;
		}
		
		if(!MobileWebCam.gCustomReceiverActive)
			CustomReceiverService.start(context);
	}

	public static void Stop(Context context, SharedPreferences prefs)
	{
		SharedPreferences.Editor edit = prefs.edit();
		edit.putBoolean("mobilewebcam_enabled", false);
		edit.commit();
		
		switch(PhotoSettings.getCamMode(prefs))
		{
		case HIDDEN:
		case BACKGROUND:
			{
				AlarmManager alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
				Intent i = new Intent(context, PhotoAlarmReceiver.class);
				PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, i, 0);
				alarmMgr.cancel(pendingIntent);
				PhotoAlarmReceiver.StopNotification(context);
			}
			break;
		case MANUAL:
		case NORMAL:
		default:
			Intent i = new Intent(context, MobileWebCam.class);
			i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			i.putExtra("command", "stop");
			context.startActivity(i);
			break;
		}

		CustomReceiverService.stop(context);
	}
}