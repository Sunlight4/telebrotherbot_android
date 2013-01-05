package com.lego.minddroid;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

public class BluetoothService extends Service {
    private static final String TAG = "BTService";
	private BTCommunicator myBTCommunicator = null;
	private int motorLeft;
	private int motorRight;
	private int directionLeft;
	private int motorAction;
	private int directionAction;
	private int directionRight;
	
	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		String mac_address = intent.getStringExtra("address");
		this.motorLeft = intent.getIntExtra("motorLeft", 0);
		this.motorRight = intent.getIntExtra("motorRight", 0);
		this.motorAction = intent.getIntExtra("motorAction", 0);
		this.directionLeft = intent.getIntExtra("directionLeft", 0);
		this.directionRight = intent.getIntExtra("directionRight", 0);
		this.directionAction = intent.getIntExtra("directionAction", 0);
        if (myBTCommunicator != null) {
            try {
                myBTCommunicator.destroyNXTconnection();
            }
            catch (IOException e) { }
        }
        createBTCommunicator();
        myBTCommunicator.setMACAddress(mac_address);
        myBTCommunicator.start();

		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				while (true) {			
					// sleep some time
					try {
					    Thread.sleep(50);
					}
					catch (InterruptedException e) {
					}

					int[] nums=downloadMotion();
					updateMotorControl(nums[0], nums[1], nums[2]);
				}
			}
		};
		new Thread(runnable).start();
		return Service.START_REDELIVER_INTENT;
	}
	
    private boolean stopAlreadySent = false;
	private Handler btcHandler;
	private Handler myHandler = new Handler();
	
    /**
     * Sends the motor control values to the communcation thread.
     * @param left The power of the left motor from 0 to 100.
     * @param rigth The power of the right motor from 0 to 100.
     */   
    public void updateMotorControl(int left, int right, int action) {

        if (myBTCommunicator != null) {
            // don't send motor stop twice
            if ((left == 0) && (right == 0) && (action == 0)) {
            	Log.d(TAG, "Sensed stop");
                if (stopAlreadySent)
                    return;
                else
                    stopAlreadySent = true;
            }
            else
                stopAlreadySent = false;         
                        
            // send messages via the handler
            sendBTCmessage(BTCommunicator.NO_DELAY, motorLeft, left * directionLeft, 0);
            sendBTCmessage(BTCommunicator.NO_DELAY, motorRight, right * directionRight, 0);
            sendBTCmessage(BTCommunicator.NO_DELAY, motorAction, action * directionAction, 0);
        }
    }    
    
    /**
     * Sends the message via the BTCommuncator to the robot.
     * @param delay time to wait before sending the message.
     * @param message the message type (as defined in BTCommucator)
     * @param value1 first parameter
     * @param value2 second parameter
     */   
    void sendBTCmessage(int delay, int message, int value1, int value2) {
    	Log.d(TAG, String.format("%s(%s, %s)", message, value1, value2));
        Bundle myBundle = new Bundle();
        myBundle.putInt("message", message);
        myBundle.putInt("value1", value1);
        myBundle.putInt("value2", value2);
        Message myMessage = Message.obtain();
        myMessage.setData(myBundle);

        if (delay == 0)
            btcHandler.sendMessage(myMessage);

        else
            btcHandler.sendMessageDelayed(myMessage, delay);
    }

	
    /**
     * Creates a new object for communication to the NXT robot via bluetooth and fetches the corresponding handler.
     */
    private void createBTCommunicator() {
        // interestingly BT adapter needs to be obtained by the UI thread - so we pass it in in the constructor
        BTConnectable connectable = new BTConnectable() {
			@Override
			public boolean isPairing() {
				return false;
			}
		};
		myBTCommunicator = new BTCommunicator(connectable, myHandler , BluetoothAdapter.getDefaultAdapter(), getResources());
        btcHandler = myBTCommunicator.getHandler();
    }

	private int[] downloadMotion() {
		// TODO Auto-generated method stub
		DefaultHttpClient client = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet("http://telebrotherbot.appspot.com/getcmd");
		try {
			HttpResponse response = client.execute(httpGet);
			String string = EntityUtils.toString(response.getEntity());
			Log.d(TAG, "response command: " + string);
			String[] strings = string.split(" ");
			int leftnum = Integer.valueOf(strings[0]);
			int rightnum = Integer.valueOf(strings[1]);
			int actionnum = Integer.valueOf(strings[2]);
			return new int[] {leftnum, rightnum, actionnum};
				 
		} catch (Throwable e) {
			Log.e(TAG, "response error", e);
		}
		return new int[] {0, 0, 0};
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
}