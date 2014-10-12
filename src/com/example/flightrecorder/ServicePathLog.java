package com.example.flightrecorder;

import java.util.Calendar;
import java.lang.Thread;

import com.example.flightrecorder.R;
import com.example.flightrecorder.LogWriter;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.Sensor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.os.PowerManager;

public class ServicePathLog extends Service implements SensorEventListener
{
	private String TAG = "ServicePathLog";
	
	private LocationManager m_locationManager = null;
	private LocationProvider m_locationProvider = null;
	private int m_startTime = 0;
	
	private String m_departure = "";
	private String m_destination = "";
	private String m_airplaneType = "";
	
	private Flight _flight = null;
	
	private PowerManager.WakeLock _wakeLock = null;
	
	private Thread.UncaughtExceptionHandler _uncaughtExceptionHandler = null;
	
	private int _dbId = -1;
	
	private int _waypointsCount = 0;
	
	private Messenger _messenger = null;
	
	private LogWriter _logWriter = new LogWriter();
	private String _fileName = "";
	
	private SensorManager _sensorManager;
	private Sensor _accelerometer;
	
	private Thread.UncaughtExceptionHandler _exceptionHandler = new Thread.UncaughtExceptionHandler()
	{
		@Override
		public void uncaughtException(Thread thread, Throwable ex)
		{
			try
			{
				if(_wakeLock != null)
				{
					if(_wakeLock.isHeld())
					{
						_wakeLock.release();
					}
				}
			}
			catch(Exception e)
			{
				Log.e(TAG, "Uncaught Exception: " + ex.getMessage());
			}
			finally
			{
				Thread.setDefaultUncaughtExceptionHandler(_uncaughtExceptionHandler);
				_uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), ex);
			}
		}
	};
	
	private ServicePathLog _this = null;
	
	private final LocationListener m_locationListener = new LocationListener()
	{
		@Override
		public void onProviderEnabled(String provider)
		{
		}
		
		@Override
	    public void onLocationChanged(Location location)
		{
			Calendar c = Calendar.getInstance();
			int t = (c.get(Calendar.HOUR_OF_DAY) * 60 * 60) + (c.get(Calendar.MINUTE) * 60) + c.get(Calendar.SECOND);
			t -= m_startTime;
			
			//_flight.addWaypoint(new Flight.Waypoint(t, location.getLatitude(), location.getLongitude(), location.getAltitude(), location.getSpeed()));
			
			String jsonMessage = "";
			
			if(_waypointsCount > 0)
			{
				jsonMessage += ",";
			}
			
			jsonMessage += "\"waypoint\":{";
			jsonMessage += "\"t\":\"" + t +"\",";
			jsonMessage += "\"lat\":\"" + location.getLatitude() + "\",";
			jsonMessage += "\"long\":\"" + location.getLongitude() + "\",";
			jsonMessage += "\"alt\":\"" + location.getAltitude() + "\",";
			jsonMessage += "\"speed\":\"" + location.getSpeed() + "\"}";
			
			_logWriter.writeToFile(jsonMessage);
			
			if(_this != null)
			{
				_waypointsCount++;
			}
		}
		
		@Override
		public void onProviderDisabled(String provider)
		{
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras)
		{
		}
	};
	
	
	
	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}
	
	@Override
	public void onCreate()
	{
		_this = this;
		
		_uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(_exceptionHandler);
		
		_sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		_accelerometer = _sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		_sensorManager.registerListener(this,  _accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
	}
	
	@Override
	public void onStart(Intent intent, int startId)
	{
		m_departure = intent.getStringExtra(getString(R.string.log_departure));
		m_destination = intent.getStringExtra(getString(R.string.log_destination));
		m_airplaneType = intent.getStringExtra(getString(R.string.log_airplane_type));
		
		//_messenger = (Messenger)intent.getExtras().get(getString(R.string.log_path_log_handler));
		
		PowerManager p = (PowerManager) getSystemService(Context.POWER_SERVICE);
		_wakeLock = p.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		_wakeLock.acquire();
		
		setupLocationProvider();
	}
	
	@Override
	public void onDestroy()
	{
		try
		{
			if(_wakeLock.isHeld())
				_wakeLock.release();
		}
		catch(Error error)
		{
			Log.e(TAG, error.getMessage());
		}

		stopLocationProvider();
	}
	
	protected void setupLocationProvider()
	{
		try
		{
			if(m_locationManager == null)
			{
				m_locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
			}
			
			boolean gpsEnabled = m_locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
			
			if(!gpsEnabled)
			{
				this.stopSelf();
			}
			
			m_locationProvider = m_locationManager.getProvider(LocationManager.GPS_PROVIDER);
			m_locationProvider.getName();
			
			m_locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 2, m_locationListener);
			
			initFlight();
			Calendar c = Calendar.getInstance();
			m_startTime = (c.get(Calendar.HOUR_OF_DAY) * 60 * 60) + (c.get(Calendar.MINUTE) * 60) + c.get(Calendar.SECOND);
			//String date = c.get(Calendar.YEAR) + "_" + c.get(Calendar.MONTH) + "_" + c.get(Calendar.DAY_OF_MONTH) + "-"
			//			+ c.get(Calendar.HOUR_OF_DAY) + "_" + c.get(Calendar.MINUTE) + "_" + c.get(Calendar.SECOND);
		}
		catch(Exception e)
		{
			Log.e(TAG, e.getMessage());
		}
	}
	
	private void stopLocationProvider()
	{
		if(m_locationManager != null)
		{
			m_locationManager.removeUpdates(m_locationListener);
		}
		
		Message msg = Message.obtain();
		
		//delete flight when no waypoints where recorded
		
		String jsonMessage = "]}";
		
		_logWriter.writeToFile(jsonMessage);
		_logWriter.closeFile();
		
		if(_waypointsCount <= 1)
		{
			
		}
		else
		{
			
		}
	}
	
	private void initFlight()
	{
		_flight = new Flight();
		
		Calendar c = Calendar.getInstance();
		//String date = c.get(Calendar.DAY_OF_MONTH) + "." + c.get(Calendar.MONTH) + " " + c.get(Calendar.HOUR_OF_DAY) + ":" + c.get(Calendar.MINUTE) + ":" + c.get(Calendar.SECOND);
		
		_fileName = "flight_" + c.get(Calendar.DAY_OF_MONTH) + "-" + c.get(Calendar.MONTH) + "_" + c.get(Calendar.HOUR_OF_DAY) + "-" + c.get(Calendar.MINUTE) + ".txt";
		_logWriter.openFile(_fileName);
		
		_flight.setDate(new Flight.Date(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH),
										c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND)));
		
		_flight.setDeparture(m_departure);
		_flight.setDestination(m_destination);
		_flight.setAirplaneType(m_airplaneType);
		
		String jsonMessage = "\"flight\": {";
		jsonMessage += "\"departure\":\"" + m_departure + "\",";
		jsonMessage += "\"destination\":\"" + m_departure + "\",";
		jsonMessage += "\"airplane\":\"" + m_departure + "\",";
		jsonMessage += "\"date\":\"" + _flight.getDate().serialize() + "\"\",";
		jsonMessage += "\"waypoints\":[";
		
		_logWriter.writeToFile(jsonMessage);
		
		_waypointsCount = 0;
	}
	
	@Override
	public void onSensorChanged(SensorEvent sensorEvent)
	{
		Sensor sensor = sensorEvent.sensor;
		
		if(sensor.getType() == Sensor.TYPE_ACCELEROMETER)
		{
			float x = sensorEvent.values[0];
			float y = sensorEvent.values[1];
			float z = sensorEvent.values[2];
			
			Calendar c = Calendar.getInstance();
			int t = (c.get(Calendar.HOUR_OF_DAY) * 60 * 60) + (c.get(Calendar.MINUTE) * 60) + c.get(Calendar.SECOND);
			t -= m_startTime;
			
			String jsonMessage = "";
			
			if(_waypointsCount > 0)
			{
				jsonMessage += ",";
			}
			
			jsonMessage += "\"acceleration\":{";
			jsonMessage += "\"t\":\"" + t + "\",";
			jsonMessage += "\"x\":\"" + x + "\",";
			jsonMessage += "\"y\":\"" + y + "\",";
			jsonMessage += "\"z\":\"" + z + "\"}";
			
			_logWriter.writeToFile(jsonMessage);
			
			_waypointsCount++;
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
	}
}
