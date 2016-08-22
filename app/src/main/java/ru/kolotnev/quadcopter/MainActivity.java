package ru.kolotnev.quadcopter;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity implements
		EncodeDisplay.StreamingListener,
		ControlSender.Listener,
		CompoundButton.OnCheckedChangeListener,
		SeekBar.OnSeekBarChangeListener {
	private static final String WIFI_SSID_PREFIX = "WK";
	private static final int WIFI_STATE_CONNECTED = 12291;
	private static final int WIFI_STATE_DISABLED = 12289;
	private static final int WIFI_STATE_NOT_CONNECTED = 12290;
	private static final int CONTROL_RANGE = 1600;
	private static Sensor sensorAccelerometer;
	private static SensorManager sensorManager;
	private static boolean isGamePadEnabled = false;
	private static boolean isAccelerometerEnabled = false;
	private TextView textViewStatus;
	private ImageView imageViewStreaming;
	private ToggleButton checkBoxImageScale;
	private ToggleButton switchStreaming;
	private ToggleButton switchControl;
	private ToggleButton switchAccelerometer;
	private ToggleButton switchGamePad;
	private ControlSender controlSender;
	private SeekBar seekBarElevation;
	private SeekBar seekBarThrottle;
	private SeekBar seekBarAileron;
	private SeekBar seekBarRudder;
	private SeekBar seekBarScrew;
	private SeekBar seekBarAux;
	private JoystickView joystickViewLeft;
	private JoystickView joystickViewRight;
	private EncodeDisplay encodeDisplay;

	private boolean isAccelerometerNotTrimmed = false;

	private SensorEventListener accelerometerListener = new SensorEventListener() {
		private final double MAX = 60.0;
		private final double MIN = 5.0;
		private double pitchZero, rollZero;

		@Override
		public void onSensorChanged(SensorEvent event) {
			float[] samples = LowPassFilter.filter(event.values);
			double pitch = samples[0] * 10;
			double roll = samples[1] * 10;
			if (isAccelerometerNotTrimmed) {
				pitchZero = pitch;
				rollZero = roll;
				isAccelerometerNotTrimmed = false;
			}
			pitch -= pitchZero;
			roll -= rollZero;

			//Log.e("Quad", "pitch=" + pitch + " roll=" + roll);

			short elevQty = 0;
			short aileQty = 0;
			if ((pitch > MIN || pitch < -MIN) || (roll > MIN || roll < -MIN)) {
				// Elevation
				if (pitch <= MAX && pitch >= -MAX) {
					elevQty = (short) (-1600.0 * pitch / 54.0);
					if (elevQty > Constant.BASEMAX) {
						elevQty = Constant.BASEMAX;
					} else if (elevQty < Constant.BASEMIN) {
						elevQty = Constant.BASEMIN;
					}
				} else if (pitch < -MAX) {
					elevQty = Constant.BASEMAX;
				} else if (pitch > MAX) {
					elevQty = Constant.BASEMIN;
				}

				// Aileron
				if (roll <= MAX && roll >= -MAX) {
					aileQty = (short) (1600.0 * roll / 54.0);
					if (aileQty > Constant.BASEMAX) {
						aileQty = Constant.BASEMAX;
					} else if (aileQty < Constant.BASEMIN) {
						aileQty = Constant.BASEMIN;
					}
				} else if (roll < -MAX) {
					aileQty = Constant.BASEMIN;
				} else if (roll > MAX) {
					aileQty = Constant.BASEMAX;
				}
			} else if (pitch <= MIN && pitch >= -MIN) {
				elevQty = 0;
				aileQty = 0;
			}

			// GSensor
			seekBarElevation.setProgress(elevQty + CONTROL_RANGE);
			seekBarRudder.setProgress(aileQty + CONTROL_RANGE);
			joystickViewLeft.setCurrentCoordinate(aileQty, elevQty);

			if (controlSender != null) {
				controlSender.elev0 = elevQty;
				controlSender.rudd0 = aileQty;
			}
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}
	};

	/**
	 * Converts int server address to string.
	 *
	 * @param i
	 * 		Server address.
	 *
	 * @return String IP.
	 */
	public static String intToIp(int i) {
		return String.valueOf(i & 255) + "." + ((i >> 8) & 255) + "." + ((i >> 16) & 255) + "." + ((i >> 24) & 255);
	}

	public void accelerometerEnable() {
		if (sensorManager != null && sensorAccelerometer != null) {
			sensorManager.registerListener(accelerometerListener, sensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
			isAccelerometerEnabled = true;
			LowPassFilter.resetFilter();
		}
	}

	public void accelerometerDisable() {
		if (sensorManager != null && sensorAccelerometer != null) {
			sensorManager.unregisterListener(accelerometerListener, sensorAccelerometer);
			isAccelerometerEnabled = false;
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		textViewStatus = (TextView) findViewById(R.id.text_status);
		imageViewStreaming = (ImageView) findViewById(R.id.image_streaming);

		checkBoxImageScale = (ToggleButton) findViewById(R.id.toggle_streaming_size);
		checkBoxImageScale.setOnCheckedChangeListener(this);

		switchStreaming = (ToggleButton) findViewById(R.id.toggle_video);
		switchStreaming.setOnCheckedChangeListener(this);

		switchControl = (ToggleButton) findViewById(R.id.toggle_control);
		switchControl.setOnCheckedChangeListener(this);

		switchAccelerometer = (ToggleButton) findViewById(R.id.toggle_accelerometer);
		switchAccelerometer.setChecked(isAccelerometerEnabled);
		switchAccelerometer.setOnCheckedChangeListener(this);

		switchGamePad = (ToggleButton) findViewById(R.id.toggle_controller);
		switchGamePad.setChecked(isGamePadEnabled);
		switchGamePad.setOnCheckedChangeListener(this);

		seekBarElevation = (SeekBar) findViewById(R.id.seek_elev);
		seekBarThrottle = (SeekBar) findViewById(R.id.seek_thro);
		seekBarAileron = (SeekBar) findViewById(R.id.seek_aile);
		seekBarRudder = (SeekBar) findViewById(R.id.seek_rudd);
		seekBarScrew = (SeekBar) findViewById(R.id.seek_screw);
		seekBarAux = (SeekBar) findViewById(R.id.seek_aux);

		encodeDisplay = EncodeDisplay.getInstance(this);
		controlSender = ControlSender.getInstance(this);

		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		if (isAccelerometerEnabled) {
			accelerometerEnable();
		}

		seekBarElevation.setOnSeekBarChangeListener(this);
		seekBarThrottle.setOnSeekBarChangeListener(this);
		seekBarAileron.setOnSeekBarChangeListener(this);
		seekBarRudder.setOnSeekBarChangeListener(this);
		seekBarScrew.setOnSeekBarChangeListener(this);
		seekBarAux.setOnSeekBarChangeListener(this);

		joystickViewLeft = (JoystickView) findViewById(R.id.joystick_left);
		joystickViewLeft.setMovementRange(CONTROL_RANGE);
		joystickViewLeft.setOnJostickMovedListener(new JoystickView.JoystickMovedListener() {
			@Override
			public void OnMoved(int pan, int tilt) {
				if (isGamePadEnabled || isAccelerometerEnabled) return;
				//Log.e("Quad", "elevation " + elevation + " rudder " + rudder);
				if (controlSender != null) {
					controlSender.elev0 = tilt;
					controlSender.rudd0 = pan;
				}
				seekBarElevation.setProgress(1600 + tilt);
				seekBarRudder.setProgress(1600 + pan);
			}

			@Override
			public void OnReleased() {

			}

			@Override
			public void OnReturnedToCenter() {

			}
		});

		joystickViewRight = (JoystickView) findViewById(R.id.joystick_right);
		joystickViewRight.setAutoReturnToCenter(true, true);
		joystickViewRight.setMovementRange(CONTROL_RANGE);
		joystickViewRight.setCurrentCoordinate(0, -1600);
		joystickViewRight.setZero(0, -1600);

		joystickViewRight.setOnJostickMovedListener(new JoystickView.JoystickMovedListener() {
			@Override
			public void OnMoved(int pan, int tilt) {
				//if (isGamePadEnabled || isAccelerometerEnabled) return;
				//Log.e("Quad", "throttle " + throttle + " aileron " + aileron);
				if (controlSender != null) {
					controlSender.thro0 = tilt;
					controlSender.aile0 = pan;
				}
				seekBarThrottle.setProgress(1600 + tilt);
				seekBarAileron.setProgress(1600 + pan);
			}

			@Override
			public void OnReleased() {

			}

			@Override
			public void OnReturnedToCenter() {

			}
		});
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		accelerometerDisable();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		//noinspection SimplifiableIfStatement
		if (id == R.id.action_settings) {
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		switch (buttonView.getId()) {
			case R.id.toggle_video:
				if (encodeDisplay != null) {
					if (!isChecked && encodeDisplay.isStreamingEnabled()) {
						encodeDisplay.stopPlayback();
					} else if (isChecked && !encodeDisplay.isStreamingEnabled()) {
						encodeDisplay.startPlayback();
					}
				}
				break;
			case R.id.toggle_control:
				if (controlSender != null) {
					if (controlSender.transferring && !isChecked) {
						controlSender.CmdSendStop();
					} else if (!controlSender.transferring && isChecked) {
						controlSender.CmdSendStart();
					}
				}
				break;
			case R.id.toggle_accelerometer:
				if (isChecked && !isAccelerometerEnabled) {
					accelerometerEnable();
				} else if (!isChecked && isAccelerometerEnabled) {
					accelerometerDisable();
				}
				break;
			case R.id.toggle_controller:
				isGamePadEnabled = isChecked;
				break;

			case R.id.toggle_streaming_size:
				imageViewStreaming.setScaleType(isChecked
						? ImageView.ScaleType.CENTER_CROP
						: ImageView.ScaleType.FIT_CENTER);
				break;
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		switch (seekBar.getId()) {
			/*case R.id.seek_elev:
				controlSender.elev0 = progress - 1600;
				break;
			case R.id.seek_thro:
				controlSender.thro0 = progress - 1600;
				break;
			case R.id.seek_aile:
				controlSender.aile0 = progress - 1600;
				break;
			case R.id.seek_rudd:
				controlSender.rudd0 = progress - 1600;
				break;*/
			case R.id.seek_screw:
				controlSender.screw0 = progress - 1600;
				break;
			case R.id.seek_aux:
				controlSender.auxIDec = (short) (progress - 1600);
				break;
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {

	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {

	}

	public int getWifiStatus() {
		WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		switch (wifiManager.getWifiState()) {
			case WifiManager.WIFI_STATE_DISABLING:
			case WifiManager.WIFI_STATE_DISABLED:
			case WifiManager.WIFI_STATE_ENABLING:
			case WifiManager.WIFI_STATE_UNKNOWN:
				return WIFI_STATE_DISABLED;
			case WifiManager.WIFI_STATE_ENABLED:
				int status = WIFI_STATE_NOT_CONNECTED;
				ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo.State state = NetworkInfo.State.UNKNOWN;
				if (Build.VERSION.SDK_INT < 21) {
					state = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
				} else {
					for (Network n : cm.getAllNetworks()) {
						NetworkInfo ni = cm.getNetworkInfo(n);
						if (ni.getType() == ConnectivityManager.TYPE_WIFI) {
							state = cm.getNetworkInfo(cm.getAllNetworks()[0]).getState();
							break;
						}
					}
				}
				if (NetworkInfo.State.CONNECTED != state) {
					return WIFI_STATE_NOT_CONNECTED;
				}
				WifiInfo info = wifiManager.getConnectionInfo();
				if (info != null) {
					String sessionName = info.getSSID();
					if (sessionName != null && !sessionName.isEmpty() && sessionName.contains(WIFI_SSID_PREFIX)) {
						status = WIFI_STATE_CONNECTED;
					}
				}
				try {
					InetAddress serverIp = InetAddress.getByName(intToIp(wifiManager.getDhcpInfo().serverAddress));
					if (status == WIFI_STATE_CONNECTED) {
						return serverIp.toString().equals("/192.168.10.1")
								? WIFI_STATE_CONNECTED
								: WIFI_STATE_NOT_CONNECTED;
					} else {
						return status;
					}
				} catch (UnknownHostException e) {
					e.printStackTrace();
					return status;
				}
			default:
				return WIFI_STATE_DISABLED;
		}
	}

	public void onClickWifiCheck(View v) {
		if (getWifiStatus() == WIFI_STATE_CONNECTED) {
			textViewStatus.setText("WIFI ENABLED AND CONNECTED");
		} else if (getWifiStatus() == WIFI_STATE_NOT_CONNECTED) {
			textViewStatus.setText("WIFI ENABLED BUT NOT CONNECTED");
		} else {
			textViewStatus.setText("WIFI NOT ENABLED");
		}
	}

	public void onClickAccelerometerTrim(View v) {
		isAccelerometerNotTrimmed = true;
	}

	@Override
	public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
		return super.dispatchKeyEvent(event);
	}

	@Override
	public boolean dispatchGenericMotionEvent(MotionEvent ev) {
		// Check that the event came from a joystick since a generic motion event
		// could be almost anything.
		if ((ev.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0
				&& ev.getAction() == MotionEvent.ACTION_MOVE) {

			int elevation = (int) (ev.getAxisValue(MotionEvent.AXIS_Y) * -CONTROL_RANGE);
			int rudder = (int) (ev.getAxisValue(MotionEvent.AXIS_X) * CONTROL_RANGE);

			int throttle = (int) (ev.getAxisValue(MotionEvent.AXIS_GAS) * CONTROL_RANGE * 2 - CONTROL_RANGE);
			int aileron = (int) (ev.getAxisValue(MotionEvent.AXIS_Z) * CONTROL_RANGE);

			if (isGamePadEnabled) {
				seekBarElevation.setProgress(elevation + CONTROL_RANGE);
				seekBarRudder.setProgress(rudder + CONTROL_RANGE);
				seekBarThrottle.setProgress(throttle + CONTROL_RANGE);
				seekBarAileron.setProgress(aileron + CONTROL_RANGE);

				if (controlSender != null) {
					controlSender.elev0 = elevation;
					controlSender.thro0 = throttle;
					if (!isAccelerometerEnabled) {
						controlSender.rudd0 = rudder;
						controlSender.aile0 = aileron;
					}
				}
			}

			Log.e("Quad", String.format("elevation %d rudder %d throttle %d aileron %d", elevation, rudder, throttle, aileron));
			return true;
		}

		//Log.e("Quad", "motion event on device " + ev.getDeviceId());

		return super.dispatchGenericMotionEvent(ev);
	}

	// Streaming

	@Override
	public void streamingStarted() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				textViewStatus.setText(textViewStatus.getText() + "\n" + "streaming started");
				switchStreaming.setOnCheckedChangeListener(null);
				switchStreaming.setChecked(true);
				switchStreaming.setOnCheckedChangeListener(MainActivity.this);
			}
		});
	}

	@Override
	public void streamingStopped() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				textViewStatus.setText(textViewStatus.getText() + "\n" + "streaming stopped");
				switchStreaming.setOnCheckedChangeListener(null);
				switchStreaming.setChecked(false);
				switchStreaming.setOnCheckedChangeListener(MainActivity.this);
			}
		});
	}

	@Override
	public void gotFrame(final Bitmap bmp) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				imageViewStreaming.setImageBitmap(bmp);
			}
		});
	}

	@Override
	public void cantConnect() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				textViewStatus.setText(textViewStatus.getText() + "\n" + "streaming can't connect");
				switchStreaming.setOnCheckedChangeListener(null);
				switchStreaming.setChecked(false);
				switchStreaming.setOnCheckedChangeListener(MainActivity.this);
			}
		});
	}

	@Override
	public void exception() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				textViewStatus.setText(textViewStatus.getText() + "\n" + "streaming exception");
				switchStreaming.setOnCheckedChangeListener(null);
				switchStreaming.setChecked(false);
				switchStreaming.setOnCheckedChangeListener(MainActivity.this);
			}
		});
	}

	// End streaming
}
