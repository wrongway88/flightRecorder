package com.example.flightrecorder;

import com.example.flightrecorder.ServicePathLog;
import com.example.flightrecorder.dialogs.DialogGPSOff;
import com.example.flightrecorder.R;

import android.location.LocationManager;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.View;

public class MainActivity extends FragmentActivity
{
	private static String TAG = "MainActivity";
	
	private static final String PREFERENCES = "flightRecorder_preferences";
	private static final String B_LOGGING = "flightRecorder_isLogging";
	
	private Boolean m_logging = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		
		SharedPreferences settings = this.getSharedPreferences(PREFERENCES, 0);
		if(settings != null)
		{
			m_logging = settings.getBoolean(B_LOGGING, false);
		}
	}

	public void toggleRecording(View view)
	{
		if(m_logging)
		{
			stopPositionLogging();
		}
		else
		{
			startPositionLogging();
		}
	}
	
	private void startPositionLogging()
    {
		LocationManager locationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
		
		if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
		{
			String departure = "";
			String arrival = "";
			String airplaneType = "";
			
	    	Intent intent = new Intent(this, ServicePathLog.class);
	    	intent.putExtra(getString(R.string.log_departure), departure);
			intent.putExtra(getString(R.string.log_destination), arrival);
			intent.putExtra(getString(R.string.log_airplane_type), airplaneType);
			this.startService(intent);
			
			m_logging = !m_logging;
			SharedPreferences settings = this.getSharedPreferences(PREFERENCES, 0);
			if(settings != null)
			{
				SharedPreferences.Editor editor = settings.edit();
				editor.putBoolean(B_LOGGING, m_logging);
				editor.commit();
			}
		}
		else
		{
			DialogGPSOff dialog = new DialogGPSOff();
			dialog.show(this.getSupportFragmentManager(), TAG);
		}
    }
	
	private void stopPositionLogging()
	{
		Intent intent = new Intent(this, ServicePathLog.class);
		this.stopService(intent);
		
		m_logging = !m_logging;
		SharedPreferences settings = this.getSharedPreferences(PREFERENCES, 0);
		if(settings != null)
		{
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean(B_LOGGING, m_logging);
			editor.commit();
		}
	}

}
