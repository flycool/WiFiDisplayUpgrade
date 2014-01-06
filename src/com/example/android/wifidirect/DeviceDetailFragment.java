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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import android.app.Activity;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.util.ContinueFTP;
import com.example.android.util.FTPUtil;
import com.example.android.wifidirect.DeviceListFragment.DeviceActionListener;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener {

	private static final String TAG = "DeviceDetailFragment";
    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    private View mContentView = null;
    private WifiP2pDevice device;
    private WifiP2pInfo info;
    ProgressDialog progressDialog = null;
    
    private TextView upgradeStatus;
    public static Activity instance = null;
    public static ContinueFTP ftp;
    private static long fileLength;
    
	@Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        instance = getActivity();
        ftp = ContinueFTP.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mContentView = inflater.inflate(R.layout.device_detail, null);
        upgradeStatus = (TextView)mContentView.findViewById(R.id.upgrade_status);
        
        mContentView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
                        "Connecting to :" + device.deviceAddress, true, true
//                        new DialogInterface.OnCancelListener() {
//
//                            @Override
//                            public void onCancel(DialogInterface dialog) {
//                                ((DeviceActionListener) getActivity()).cancelDisconnect();
//                            }
//                        }
                        );
                ((DeviceActionListener) getActivity()).connect(config);

            }
        });

        mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        ((DeviceActionListener) getActivity()).disconnect();
                    }
                });

        mContentView.findViewById(R.id.btn_start_client).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Allow user to pick an image from Gallery or other
                        // registered apps
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
                    }
                });
        
        return mContentView;
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

    	if(requestCode == CHOOSE_FILE_RESULT_CODE) {
	        // User has picked an image. Transfer it to group owner i.e peer using
	        // FileTransferService.
	        Uri uri = data.getData();
	        TextView statusText = (TextView) mContentView.findViewById(R.id.status_text);
	        statusText.setText("Sending: " + uri);
	        Log.d(WiFiDirectActivity.TAG, "Intent----------- " + uri);
	        Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
	        serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
	        serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString());
	        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
	                info.groupOwnerAddress.getHostAddress());
	        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988);
	        getActivity().startService(serviceIntent);
	        
    	}
    }

    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        this.info = info;
        this.getView().setVisibility(View.VISIBLE);
        
        // The owner IP is now known.
        TextView view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(getResources().getString(R.string.group_owner_text)
                + ((info.isGroupOwner == true) ? getResources().getString(R.string.yes)
                        : getResources().getString(R.string.no)));

        // InetAddress from WifiP2pInfo struct.
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress());

        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.
        if (info.groupFormed && info.isGroupOwner) {
            new FileServerAsyncTask(getActivity(), mContentView).execute();
        } else if (info.groupFormed) {
            // The other device acts as the client. In this case, we enable the
            // get file button.
            //mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);
            //((TextView) mContentView.findViewById(R.id.status_text)).setText(getResources()
              //      .getString(R.string.client_text));
        }

        // hide the connect button
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
    }

    /**
     * Updates the UI with device data
     * 
     * @param device the device to be displayed
     */
    public void showDetails(WifiP2pDevice device) {
        this.device = device;
        this.getView().setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(device.deviceAddress);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(device.toString());

    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.status_text);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.upgrade_status);
        view.setText(R.string.empty);
        mContentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
        mContentView.findViewById(R.id.btn_upload).setVisibility(View.GONE);
        mContentView.findViewById(R.id.btn_check_fwversion).setVisibility(View.GONE);
        mContentView.findViewById(R.id.btn_upgrade).setVisibility(View.GONE);
        this.getView().setVisibility(View.GONE);
        
        resetFTP();
        resetUploadFlag();
    }
    
    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    public static class FileServerAsyncTask extends AsyncTask<Void, Void, String> {
        private Context context;
        private TextView statusText;
        private View contentView;
        private Button uploadBtn;
        
        /**
         * @param context
         * @param statusText
         */
        public FileServerAsyncTask(Context context, View contentView) {
            this.context = context;
            this.contentView = contentView;
            statusText = (TextView)contentView.findViewById(R.id.status_text);
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
            	String ip = "";
            	for (int i=0; i<10; i++) {
            		if (ip.equals("")) {
            			ServerSocket serverSocket = new ServerSocket(2323);
                        Socket client = serverSocket.accept();
                        InputStream inputstream = client.getInputStream();
                        ip = FTPUtil.streamToString(inputstream);
                        Log.d("System.out", "device ip : " + ip);
                        serverSocket.close();
            		} else {
            			break;
            		}
            	}
                return ip;
            } catch (IOException e) {
                Log.e("System.out", "get ip: " + e.getMessage());
                return null;
            }
        }
        
        @Override
        protected void onPreExecute() {
            statusText.setText("Opening a server socket");
        }
        
        @Override
        protected void onPostExecute(final String result) {
            if (result != null && !result.equals("")) {
            	statusText.setText(result);
            	
            	new CheckVersionAsyncTask(context, contentView).execute(result);
            	
            	showUpdateBtn();
            	
        	    final Button checkFwversion = (Button) contentView.findViewById(R.id.btn_check_fwversion);
            	//checkFwversion.setVisibility(View.VISIBLE);
            	checkFwversion.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						new CheckVersionAsyncTask(context, contentView).execute(result);
					}
				});
            	
            	uploadBtn = (Button) contentView.findViewById(R.id.btn_upload);
            	uploadBtn.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						Intent intent = new Intent(context, FileListActivity.class);
						intent.putExtra("device_ip", result);
						context.startActivity(intent);
					}
				});
            }
        }
        
        private void showUpdateBtn() {
        	SharedPreferences sf = instance.getSharedPreferences("upload_process", 0);
            int upload_done = sf.getInt("upload_done", 0);
            if (upload_done == 1) {
            	contentView.findViewById(R.id.btn_upload).setVisibility(View.VISIBLE);
            	Button btnUpgrade = (Button)contentView.findViewById(R.id.btn_upgrade);
            	btnUpgrade.setVisibility(View.VISIBLE);
            	btnUpgrade.setOnClickListener(new OnClickListener() {
        			@Override
        			public void onClick(View v) {
        				new CheckUpgradFileAsyncTask(DeviceDetailFragment.instance).execute(fileLength);
        			}
        		});
            	
                fileLength = sf.getLong("file_length", 0);
            }
        }
        
    }//end task
    
    public static class CheckVersionAsyncTask extends AsyncTask<String, Void, Boolean> {
         private TextView updateText;
         private View contentView;
         private Button uploadBtn;

         public CheckVersionAsyncTask(Context context, View contentView) {
        	 this.contentView = contentView;
             updateText = (TextView)contentView.findViewById(R.id.upgrade_status);
         }
         
		@Override
		protected Boolean doInBackground(String... params) {
			final String ip = params[0];
			final String localPath = Environment.getExternalStorageDirectory() + "/sysinfo";
        	//download sysinfo file
			boolean ok = false;
    		String ret = downloadVersionInfoFile(ip, localPath, "/sysinfo");
			if (ret.equals("Download_From_Break_Success") ||
					ret.equals("Download_New_Success")) {
				ok = checkFWVersion(localPath);
				if (ok) {
					File f = new File(localPath);
					if (f.exists()) {
						f.delete();
					}
				}
        	}
			return ok;
		}
    	
		@Override
		protected void onPostExecute(Boolean result) {
			if (result) {
				uploadBtn = (Button) contentView.findViewById(R.id.btn_upload);
				uploadBtn.setVisibility(View.VISIBLE);
				final SharedPreferences sf = instance.getSharedPreferences("upload_process", 0);
	            final int uploadFlag = sf.getInt("upload_done", 0);
	            if (uploadFlag == 1) {
	            	updateText.setText("upload file ok, you can upgrade the device");
	            } else {
	            	updateText.setText("version update, please upload the install.img file");
	            }
			}
		}
		
    } //end task
    
    public static class CheckUpgradFileAsyncTask extends AsyncTask<Long, Void, Boolean> {
   	 	private Context context;
        public CheckUpgradFileAsyncTask(Context context) {
        	this.context = context;
        }
		@Override
		protected Boolean doInBackground(Long... params) {
			return ((DeviceUpgradeListener)context).checkFWFile(params[0]);
		}
		@Override
		protected void onPostExecute(Boolean result) {
			if (result) {
				resetUploadFlag();
				
				new Thread(new Runnable(){@Override
				public void run() {
					try {
					ftp.writeRemoteFile("/upgrade", "upgrade");
						if (ftp.isConnected()) {
							ftp.disconnect();
							ftp = null;
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}}).start();
			} else {
				Toast.makeText(context, "file error", Toast.LENGTH_LONG).show();
			}
		}
		
   } //end task
    
    public static String downloadVersionInfoFile(String ip, String localPath, String fileName) {
    	String downloadResult = "";
    	try {
    		if (!ftp.isConnected()) {
        		// ftp first connect the whole process only this one connect
        		boolean result = ftp.connect(ip, ContinueFTP.PORT, ContinueFTP.USERNAME, ContinueFTP.PASSWORD);
    			if (result) {
    				downloadResult = ftp.downloadForStupidFTP(fileName, localPath);
    	    	}
        	} else {
        		downloadResult = ftp.downloadForStupidFTP(fileName, localPath);
        	}
    	} catch (Exception e) {
			Log.e("System.out", TAG + " downloadVersionInfoFile() " + e.getMessage());
		}
    	return downloadResult;
    }
    
    public static boolean checkFWVersion(String path) {
    	File file = new File(path);
    	if (!file.exists() || file.length() <= 0) {
    		return false;
    	}
    	FileInputStream inputStream = null;
		try {
			inputStream = new FileInputStream(file);
			String result = FTPUtil.parseStreamContent(inputStream, 2, " ");
			long version = FTPUtil.parseFWVersion(result);
			Log.d("System.out", TAG + " checkFWVersion() FW version=" + version);
			if (version + 1 > version) {
        		return true;
            }
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		}
    	return false;
    }
    
    //call back from WiFiDirectActivity
    public static boolean checkFWLength(String path, long length) {
    	boolean ok = false;
    	File file = new File(path);
    	if (!file.exists() || file.length() <= 0) {
    		return ok;
    	}
    	FileInputStream inputStream = null;
		try {
			inputStream = new FileInputStream(file);
			String result = FTPUtil.parseStreamContent(inputStream, 0, "=");
			long len = Long.valueOf(result).longValue();
			Log.d("System.out", TAG + " checkFWLength() local FW size=" + length);
			Log.d("System.out", TAG + " checkFWLength() remote FW size=" + len);
			if (len == length) {
				ok = true;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			ok = false;
		}
		return ok;
    }
    
    public static void resetFTP() {
    	if (ftp != null && ftp.isConnected()) {
    		try {
				ftp.disconnect();
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
    }
    
    public void checkForUpdate() {
    	mContentView.findViewById(R.id.btn_check_fwversion).performClick();
    }
    
    public static void resetUploadFlag() {
    	SharedPreferences uploadFlag = DeviceDetailFragment.instance.getSharedPreferences("upload_process", 0);
		SharedPreferences.Editor editor = uploadFlag.edit();
		editor.putInt("upload_done", 0);
		editor.putLong("file_length", 0);
		fileLength = 0;
		editor.commit();
    }
    
    /**
     *	ftp flow 
     *	get /sysinfo (get system info)
	 *	put install.img /fw (upload firmware)
	 *	get /fwerify (Check firmware upload result)
	 *	put xxxx /upgrade (Start system upgrade and reboot)
     * 
     */
    
    public void showUpdateBtn() {
    	upgradeStatus.setText("upload file ok, you can upgrade the device");
    	mContentView.findViewById(R.id.btn_upload).setVisibility(View.VISIBLE);
    	Button btnUpgrade = (Button)mContentView.findViewById(R.id.btn_upgrade);
    	btnUpgrade.setVisibility(View.VISIBLE);
    	btnUpgrade.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				new CheckUpgradFileAsyncTask(DeviceDetailFragment.instance).execute(fileLength);
			}
		});
    	
    	SharedPreferences sf = getActivity().getSharedPreferences("upload_process", 0);
        fileLength = sf.getLong("file_length", 0);
    }
    
    public void hideUpgradeBtn() {
    	mContentView.findViewById(R.id.btn_upgrade).setVisibility(View.GONE);
    }
    
    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d(TAG, e.toString());
            return false;
        }
        return true;
    }
    
    public interface DeviceUpgradeListener {
		boolean checkFWFile(long size);
		void uploadFile(String deviceIp, String fileName, String currentPath);
	}

}
