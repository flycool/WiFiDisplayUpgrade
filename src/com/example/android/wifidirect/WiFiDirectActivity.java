/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.wifidirect;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.example.android.util.ContinueFTP;
import com.example.android.wifidirect.DeviceDetailFragment.DeviceUpgradeListener;
import com.example.android.wifidirect.DeviceListFragment.DeviceActionListener;

/**
 * An activity that uses WiFi Direct APIs to discover and connect with available
 * devices. WiFi Direct APIs are asynchronous and rely on callback mechanism
 * using interfaces to notify the application of operation success or failure.
 * The application should also register a BroadcastReceiver for notification of
 * WiFi state related events.
 */
public class WiFiDirectActivity extends Activity implements ChannelListener, DeviceActionListener,
											DeviceUpgradeListener {

    public static final String TAG = "wifidirectdemo";
    private WifiP2pManager manager;
    private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = false;

    private final IntentFilter intentFilter = new IntentFilter();
    private Channel channel;
    private BroadcastReceiver receiver = null;
    
    public static WiFiDirectActivity instance = null;
    
    //upload
    private int countThread;
	private SparseArray<Handler> map = new SparseArray<Handler>();
	private static SparseArray<MutipleNotification> mMutipleNotification = new SparseArray<MutipleNotification>();
	private String mDeviceIp;
	private String mFileName;
	private String mCurrentPath;
	
    /**
     * @param isWifiP2pEnabled the isWifiP2pEnabled to set
     */
    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        instance = this;
        
        // add necessary intent values to be matched.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        
    }
    
    /** register the BroadcastReceiver with the intent values to be matched */
    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }
    
    /**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event.
     */
    public void resetData() {
        DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager()
                .findFragmentById(R.id.frag_list);
        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
        if (fragmentList != null) {
            fragmentList.clearPeers();
        }
        if (fragmentDetails != null) {
            fragmentDetails.resetViews();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_items, menu);
        return true;
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.atn_direct_enable:
                if (manager != null && channel != null) {

                    // Since this is the system wireless settings activity, it's
                    // not going to send us a result. We will be notified by
                    // WiFiDeviceBroadcastReceiver instead.

                    startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                } else {
                    Log.e(TAG, "channel or manager is null");
                }
                return true;

            case R.id.atn_direct_discover:
                if (!isWifiP2pEnabled) {
                    Toast.makeText(WiFiDirectActivity.this, R.string.p2p_off_warning,
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
                final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager()
                        .findFragmentById(R.id.frag_list);
                fragment.onInitiateDiscovery();
                manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        Toast.makeText(WiFiDirectActivity.this, "Discovery Initiated",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(WiFiDirectActivity.this, "Discovery Failed : " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                    }
                });
                return true;
            case R.id.atn_upload:
            	//display sdcard file list
            	Intent intent = new Intent(this, FileListActivity.class);
            	intent.putExtra("device_ip", "192.168.1.170");
            	startActivity(intent);
            	return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    @Override
    public void showDetails(WifiP2pDevice device) {
        DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
        fragment.showDetails(device);

    }

    @Override
    public void connect(WifiP2pConfig config) {
        manager.connect(channel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(WiFiDirectActivity.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void disconnect() {
        final DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
        fragment.resetViews();
        manager.removeGroup(channel, new ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);

            }

            @Override
            public void onSuccess() {
                fragment.getView().setVisibility(View.GONE);
            }

        });
    }

    @Override
    public void onChannelDisconnected() {
        // we will try once more
        if (manager != null && !retryChannel) {
            Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
            resetData();
            retryChannel = true;
            manager.initialize(this, getMainLooper(), this);
        } else {
            Toast.makeText(this,
                    "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void cancelDisconnect() {

        /*
         * A cancel abort request by user. Disconnect i.e. removeGroup if
         * already connected. Else, request WifiP2pManager to abort the ongoing
         * request
         */
        if (manager != null) {
            final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager()
                    .findFragmentById(R.id.frag_list);
            if (fragment.getDevice() == null
                    || fragment.getDevice().status == WifiP2pDevice.CONNECTED) {
                disconnect();
            } else if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE
                    || fragment.getDevice().status == WifiP2pDevice.INVITED) {

                manager.cancelConnect(channel, new ActionListener() {

                    @Override
                    public void onSuccess() {
                        Toast.makeText(WiFiDirectActivity.this, "Aborting connection",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(WiFiDirectActivity.this,
                                "Connect abort request failed. Reason Code: " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

    }
    
    private ProgressDialog dialog;
    private Handler mHandler = new Handler(){@Override
    public void handleMessage(Message msg) {
    	switch (msg.what) {
		case 1:
			dialog = ProgressDialog.show(WiFiDirectActivity.this, "Press back to cancel", "check FW file", true, true);
			break;
		case SHOW_MESSAGE:
			String message = (String)msg.obj;
			Toast.makeText(WiFiDirectActivity.this, message, Toast.LENGTH_LONG).show();
			break;
		}
    }};

	@Override
	public void uploadFile(final String deviceIp, final String fileName, final String currentPath) {
		mDeviceIp = deviceIp;
		mFileName = fileName;
		mCurrentPath = currentPath;
		System.out.println("call back uploadfile=====");
		for (int i=1; i<=mMutipleNotification.size(); i++) {
			MutipleNotification mn = mMutipleNotification.get(i);
			final String uploadingFileName = mn.getFileName();
			if (uploadingFileName != null && uploadingFileName.equals(fileName)) {
				showMessage(fileName + " uploading");
				return;
			}
		}
		
		// TODO start a Service to uploadFile
		Intent receveiverIntent = new Intent(this, UploadResultReceiver.class);
		PendingIntent pi = PendingIntent.getBroadcast(this, 0, receveiverIntent, 0);
		Bundle bundle = new Bundle();
		bundle.putParcelable("receiver", pi);
		
		Intent serviceIntent = new Intent(this, FileUploadService.class);
		
		serviceIntent.putExtra("deviceIp", deviceIp);
		serviceIntent.putExtra("path", currentPath + "/" + fileName);
		
		serviceIntent.putExtras(bundle);
		
		startService(serviceIntent);
		
		
		
		
		/*new Thread(new Runnable(){@Override
			public void run() {
				countThread++;
				MutipleNotification mNotification  = new MutipleNotification(WiFiDirectActivity.this);
				map.put(countThread, mNotification.getmHandler());
				mMutipleNotification.put(countThread, mNotification);
				
				uploadFile(deviceIp, fileName, currentPath, countThread, map);
		}}).start();*/
		
	}
	
	private void uploadFile(String deviceIp, String fileName, String currentPath, int count, SparseArray<Handler> map) {
		final DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
		ContinueFTP ftpClient = DeviceDetailFragment.ftp;
		try {
			//boolean result = ftpClient.connect(deviceIp, ContinueFTP.PORT, ContinueFTP.USERNAME, ContinueFTP.PASSWORD);
			if (ftpClient != null) {
				String remote = fileName;
				if (fileName.equals("install.img")) {
					remote = "/fw";
				}
				String local = currentPath + "/" + fileName;
				String uploadResult = ftpClient.upload(local, remote, count, map);
				Log.d("System.out", "upload result : " + uploadResult);
				
				if (uploadResult.equals("Upload_From_Break_Success") || uploadResult.equals("Upload_New_File_Success")) {
					showMessage(fileName + " " + getString(R.string.upload_success));
					mMutipleNotification.remove(countThread);
					countThread--;
					if (countThread == 0) {
						fragment.showUpdateBtn();
					}
					
				}
			}
		} catch (Exception e) {
			mMutipleNotification.remove(countThread);
			countThread--;
			showMessage("error: upload install.img again \n" + e.getMessage() + "\n" + countThread);
			e.printStackTrace();
		}
	}
	
	// this is work at the separate thread to UI
	@Override
	public boolean checkFWFile(long size) {
		final DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
//		Message msg = mHandler.obtainMessage();
//		msg.what = 1;
//		mHandler.sendMessage(msg);
		
		String local = mCurrentPath + "/" + mFileName;
		long length = new File(local).length();
		
		final String localPath = Environment.getExternalStorageDirectory() + "/fwverify"; 
		String result = DeviceDetailFragment.downloadVersionInfoFile(mDeviceIp, localPath, "/fwverify");
		if (result.equals("Download_From_Break_Success") ||
				result.equals("Download_New_Success")) {
			System.out.println("length================" + size);
			System.out.println("ip================" + mDeviceIp);
			if (DeviceDetailFragment.checkFWLength(localPath, size)) {
				System.out.println("length=========ok=======" + length);
				
//				try {
//					Thread.sleep(500);
//				} catch (InterruptedException e) {
//					e.printStackTrace();
//				}
//				
//				if (dialog != null && dialog.isShowing()) {
//					dialog.dismiss();
//				}
				return true;
			}
		}
		
		return false;
	}
	
	private static final int SHOW_MESSAGE = 2;
	private void showMessage(String message) {
		Message msg = mHandler.obtainMessage();
		msg.obj = message;
		msg.what = SHOW_MESSAGE;
		mHandler.sendMessage(msg);
	}
	
	public static class UploadResultReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			System.out.println("onReceive=====================");
			final DeviceDetailFragment fragment = (DeviceDetailFragment) DeviceDetailFragment.instance.getFragmentManager()
	                .findFragmentById(R.id.frag_detail);
			if (fragment != null) {
				fragment.showUpdateBtn();
			}
			
		}
		
	}
	
}
