package kinecontrolmodularmusic;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import com.example.kinecontrolmodularmusic.R;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPortOut;

import nl.littlerobots.bean.Bean;
import nl.littlerobots.bean.BeanDiscoveryListener;
import nl.littlerobots.bean.BeanListener;
import nl.littlerobots.bean.BeanManager;
import nl.littlerobots.bean.message.Callback;
import nl.littlerobots.bean.message.SketchMetaData;

public class MainActivity extends Activity implements SensorEventListener {
	/* Private */
	private SensorManager sensorManager;
	private Sensor accelerometer;
	private Sensor gyroscope;
	
	/* UI Elements */
	private TextView textAccelX, textAccelY, textAccelZ;
	private TextView textGyroX, textGyroY, textGyroZ;
	private TextView textConnectionStatus;
  private TextView textBeanMessage;
  private TextView textBeanMessage2;
	
	private EditText field_ipaddr, field_port;
	
	/* Used to send OSC messages */
	private String receiverAddr;
	private OSCPortOut outport;
	private Thread outputThread;
	
	private float accelX, accelY, accelZ;
	private float gyroX, gyroY, gyroZ;

  private HashMap<String, String> beanMessages = new HashMap<String, String>();
	
	/* Used to communicate with Beans */
  private BeanDiscoveryListener beanDiscoveryListener = new BeanDiscoveryListener() {
    @Override
    public void onBeanDiscovered(Bean bean) {
      Log.w("Bean", "Bean Discovered!");

      attemptConnectionToBean(bean);
    }

    @Override
    public void onDiscoveryComplete() {
      Log.w("Bean", "Discovery Complete");
    }
  };

  private ArrayList<BeanSerialListener> beanList = new ArrayList<BeanSerialListener>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		init();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		sensorManager.unregisterListener(this);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		registerSensors();
	}
	
	@Override
  protected void onDestroy() {
    disconnectAllBeans();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		
	}

	@Override
	public void onSensorChanged(SensorEvent sensorEvent) {
		Sensor sensor = sensorEvent.sensor;
		
		switch (sensor.getType()) {
		case Sensor.TYPE_ACCELEROMETER:
			accelX = sensorEvent.values[0];
			accelY = sensorEvent.values[1];
			accelZ = sensorEvent.values[2];
			
	        textAccelX.setText("Accelerometer X: " + accelX);
	        textAccelY.setText("Accelerometer Y: " + accelY);
	        textAccelZ.setText("Accelerometer Z: " + accelZ);
	        
			break;
		case Sensor.TYPE_GYROSCOPE:
			gyroX = sensorEvent.values[0];
			gyroY = sensorEvent.values[1];
			gyroZ = sensorEvent.values[2];
			
	        textGyroX.setText("Gyroscope X: " + gyroX);
	        textGyroY.setText("Gyroscope Y: " + gyroY);
	        textGyroZ.setText("Gyroscope Z: " + gyroZ);
			
			break;
		}
	}
	
	private void init() {
		
		registerSensors();
		
		initViews();
		
		initThreads();

    scanForBeans();
	}
	
	private void registerSensors() {
		sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
	}
	
	private void initViews() {
		textAccelX = (TextView)findViewById(R.id.accelX);
		textAccelY = (TextView)findViewById(R.id.accelY);
		textAccelZ = (TextView)findViewById(R.id.accelZ);
		
		textAccelX.setText("Accelerometer X: ");
		textAccelY.setText("Accelerometer Y: ");
		textAccelZ.setText("Accelerometer Z: ");
		
		textGyroX = (TextView)findViewById(R.id.gyroX);
		textGyroY = (TextView)findViewById(R.id.gyroY);
		textGyroZ = (TextView)findViewById(R.id.gyroZ);
		
		textGyroX.setText("Gyroscope X: ");
		textGyroY.setText("Gyroscope Y: ");
		textGyroZ.setText("Gyroscope Z: ");
		
		textConnectionStatus = (TextView)findViewById(R.id.connectionStatus);
		textConnectionStatus.setText("Disconnected.");

    textBeanMessage = (TextView)findViewById(R.id.beanMessage);
    textBeanMessage2 = (TextView)findViewById(R.id.beanMessage);
		
		field_ipaddr = (EditText)findViewById(R.id.ipaddr);
		field_port = (EditText)findViewById(R.id.port);
	}
	
	private void initThreads() {
		outputThread = new Thread() {
			@Override
			public void run() {
				while (true) {
					if (outport != null) {
						Object[] args = new Object[7];
            args[0] = "Android";
						args[1] = accelX;
						args[2] = accelY;
						args[3] = accelZ;
						args[4] = gyroX;
						args[5] = gyroY;
						args[6] = gyroZ;

            ArrayList<OSCMessage> beanOSCMessages = new ArrayList<OSCMessage>();

            for (String s : beanMessages.values()) {
              Object[] beanArgs = new Object[1];
              beanArgs[0] = s;
              beanOSCMessages.add(new OSCMessage(receiverAddr, beanArgs));

              Log.w("Adding Bean Message", s);
            }
            Log.w("Bean", "Finished adding messages");

						OSCMessage msg = new OSCMessage(receiverAddr, args);
						
						try {
							outport.send(msg);
              for (OSCMessage m : beanOSCMessages) {
                outport.send(m);
              }
						} catch (Exception e) {
							Log.w("Failed to send message: " + e.getClass().getName(), e.getMessage());
						}
					}
					
					try {
						sleep(50);
					} catch (Exception e) {
						Log.w(e.getClass().getName(), "outputThread failed to sleep");
					}
				}
			}
		};
		
		outputThread.start();
	}

  private void scanForBeans() {
    disconnectAllBeans();
    BeanManager.getInstance().startDiscovery(beanDiscoveryListener);
  }

	public void updateIpAddr(View view) {
		Task_ConnectToComputer task = new Task_ConnectToComputer();
		task.execute();
	}

  public void scanForBeans(View view) {
    disconnectAllBeans();
    Log.w("Bean", "Scanning for beans");
    BeanManager.getInstance().startDiscovery(beanDiscoveryListener);
  }

  private void attemptConnectionToBean(Bean bean) {
    Log.w("Bean", "Attempting connection to bean");
    BeanListener beanListener = new BeanSerialListener(bean, beanList.size());
    bean.connect(this, beanListener);
  }

  private void disconnectAllBeans() {
    for(BeanSerialListener b : beanList) {
      b.disconnect();
    }
  }
	
	private class Task_ConnectToComputer extends AsyncTask<Void,Void,Void> {
		
		private String address;
		private InetAddress connectedIP;
		private int port;
		private boolean portValid;
		
		@Override
		protected Void doInBackground(Void ... params) {
			if (outport != null) {
				outport.close();
				outport = null;
			}
			
			String portStr = field_port.getText().toString();
			if (portStr.equals("")) {
				portValid = false;
				return null;
			}
			
			port = Integer.parseInt(portStr);
			
			if (port < 1 || port > 65535) {
				portValid = false;
				return null;
			}
			else {
				portValid = true;
			}
			address = field_ipaddr.getText().toString();
			
			try {
				connectedIP = InetAddress.getByName(address);
				
				outport = new OSCPortOut(connectedIP, port);
				receiverAddr = address;
				
			} catch (UnknownHostException e) {
				connectedIP = null;
			} catch (Exception e) {
				connectedIP = null;
				Log.w("Exception caught", e.getClass().getName());
			}
			
			return null;
		}
		
		@Override
		protected void onPostExecute(Void params) {
			if (portValid == false) {
				textConnectionStatus.setText("Invalid port specified. Disconnected.");
			}
			else if (connectedIP == null) {
				textConnectionStatus.setText("Could not find host. Disconnected.");
			}
			else {
				textConnectionStatus.setText("Connected to " + connectedIP.getHostAddress() + ":" + port + ".");
			}
		}
	}

  private class BeanSerialListener implements BeanListener {
    private Bean bean;

    public BeanSerialListener(Bean bean, int ID) {
      this.bean = bean;
    }
    public void disconnect() {
      bean.disconnect();
    }


    @Override
    public void onConnected() {
      Log.w("Bean", "Connected");
      beanList.add(this);
    }

    @Override
    public void onConnectionFailed() {
      Log.w("Bean", "Connection failed");
    }

    @Override
    public void onDisconnected() {
      Log.w("Bean", "Disconnected from bean");
    }

    @Override
    public void onSerialMessageReceived(byte[] bytes) {
      Log.v("Bean Serial Message", new String(bytes));

      String str = new String(bytes);
      int beanIdx = str.lastIndexOf("bean");

      if (beanIdx != -1) {
        Log.w("Bean Serial Message", str);
        String key = str.substring(0,beanIdx);

        Log.w("Bean Key", key);
        beanMessages.put(key, str);
      }
    }

    @Override
    public void onScratchValueChanged(int i, byte[] bytes) {
      Log.w("Bean", "Disconnected from bean");
    }
  }
}