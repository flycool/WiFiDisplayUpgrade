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
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.example.android.util.ContinueFTP;
import com.example.android.util.FTPUtil;
import com.example.android.wifidirect.DeviceListFragment.DeviceActionListener;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener {

    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    private View mContentView = null;
    private WifiP2pDevice device;
    private WifiP2pInfo info;
    ProgressDialog progressDialog = null;
    
    public static Activity instance = null;
    
	@Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        instance = getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mContentView = inflater.inflate(R.layout.device_detail, null);
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
//            new FileServerAsyncTask(getActivity(), mContentView.findViewById(R.id.status_text)).execute();
            new FileServerAsyncTask(getActivity(), mContentView).execute();
        } else if (info.groupFormed) {
            // The other device acts as the client. In this case, we enable the
            // get file button.
            mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);
            ((TextView) mContentView.findViewById(R.id.status_text)).setText(getResources()
                    .getString(R.string.client_text));
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
        mContentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
        mContentView.findViewById(R.id.btn_upload).setVisibility(View.GONE);
        mContentView.findViewById(R.id.btn_check_fwversion).setVisibility(View.GONE);
        this.getView().setVisibility(View.GONE);
    }
    
    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    public static class FileServerAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private TextView statusText;
        private View contentView;
        private ProgressDialog dialog;
        private Button uploadBtn;
        
        private Handler handler = new Handler(){@Override
        public void handleMessage(Message msg) {
        	switch (msg.what) {
			case 1:
				uploadBtn.setVisibility(View.VISIBLE);
				break;
			}
        }};
        
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
                ServerSocket serverSocket = new ServerSocket(2323);
                Log.d(WiFiDirectActivity.TAG, "Server: Socket opened");
                Socket client = serverSocket.accept();
                Log.d(WiFiDirectActivity.TAG, "Server: connection done");
                
                /*final File f = new File(Environment.getExternalStorageDirectory() + "/"
                        + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                        + ".jpg");

                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();*/

                //Log.d(WiFiDirectActivity.TAG, "server: copying files " + f.toString());
                InputStream inputstream = client.getInputStream();
                String ip = FTPUtil.streamToString(inputstream);
                Log.d(WiFiDirectActivity.TAG, "device ip : " + ip);
                
                //copyFile(inputstream, new FileOutputStream(f));
                serverSocket.close();
                //return f.getAbsolutePath();
                //return ip;
                return ip;
            } catch (IOException e) {
                Log.e(WiFiDirectActivity.TAG, "" + e.getMessage());
                return null;
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(final String result) {
            /*if (result != null) {
                statusText.setText("File copied - " + result);
                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse("file://" + result), "image/*");
                context.startActivity(intent);
            }*/
            if (result != null) {
            	statusText.setText(result);
        	    final Button checkFwversion = (Button) contentView.findViewById(R.id.btn_check_fwversion);
        	    uploadBtn = (Button) contentView.findViewById(R.id.btn_upload);
            	checkFwversion.setVisibility(View.VISIBLE);
            	
            	checkFwversion.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						checkForUpdate(context, result);
					}
				});
            	
            	uploadBtn.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						Intent intent = new Intent(context, FileListActivity.class);
						intent.putExtra("device_ip", result);
						context.startActivity(intent);
					}
				});
            }
            statusText.setText(result != null ? result : "null");
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            statusText.setText("Opening a server socket");
        }

        private void checkForUpdate(Context context, final String ip) {
        	if (dialog != null && dialog.isShowing()) {
        		dialog.dismiss();
            }
        	dialog = ProgressDialog.show(context, "Press back to cancel",
                    "Check for update", true, true);
        	
        	final String localPath = Environment.getExternalStorageDirectory() + "/"  + "sysinfo";
        	//download sysinfo file
        	new Thread(new Runnable(){@Override
        	public void run() {
        		String ret = downloadVersionInfoFile(ip, localPath);
    			if (ret.equals("Download_From_Break_Success") ||
    					ret.equals("Download_New_Success")) {
    				if (checkFWVersion(localPath)) {
    					if (dialog != null && dialog.isShowing()) {
    						dialog.dismiss();
    					}
    					Message msg = handler.obtainMessage();
    					msg.what = 1;
    					handler.sendMessage(msg);
    				}
	        	}
        	}}).start();
        }
        
        private String downloadVersionInfoFile(String ip, String localPath) {
        	ContinueFTP ftp = new ContinueFTP(context);
        	String downloadResult = null;
        	try {
            	if (!ftp.isConnected()) {
            		boolean result = ftp.connect(ip, ContinueFTP.PORT, ContinueFTP.USERNAME, ContinueFTP.PASSWORD);
        			if (result) {
        				downloadResult = ftp.downloadForStupidFTP("/sysinfo", localPath);
        	    	}
            	} else {
            		downloadResult = ftp.downloadForStupidFTP("/sysinfo", localPath);
            	}
        	} catch (IOException e) {
        		e.printStackTrace();
        	}
        	return downloadResult;
        }
        
        private boolean checkFWVersion(String path) {
        	File file = new File(path);
        	if (!file.exists() || file.length() <= 0) {
        		return false;
        	}
        	FileInputStream inputStream = null;
			try {
				inputStream = new FileInputStream(file);
				String result = FTPUtil.parseStreamContent(inputStream, 2, " ");
				int version = FTPUtil.parseFWVersion(result);
				Log.d("System.out", "FW version=" + version);
				if (ContinueFTP.FW_VERSION > version) {
	        		return true;
	            }
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				return false;
			}
        	return false;
        }
    }
    
    public String downloadVersionInfoFile(String ip, String localPath) {
    	ContinueFTP ftp = new ContinueFTP(getActivity());
    	String downloadResult = null;
    	try {
        	if (!ftp.isConnected()) {
        		boolean result = ftp.connect(ip, ContinueFTP.PORT, ContinueFTP.USERNAME, ContinueFTP.PASSWORD);
    			if (result) {
    				downloadResult = ftp.downloadForStupidFTP("/fwerify.txt", localPath);
    	    	}
        	} else {
        		downloadResult = ftp.downloadForStupidFTP("/fwerify.txt", localPath);
        	}
    	} catch (IOException e) {
    		e.printStackTrace();
    	}
    	return downloadResult;
    }
    
    
    public boolean checkFWLength(String path, long length) {
    	File file = new File(path);
    	if (!file.exists() || file.length() <= 0) {
    		return false;
    	}
    	FileInputStream inputStream = null;
		try {
			inputStream = new FileInputStream(file);
			String result = FTPUtil.parseStreamContent(inputStream, 0, "=");
			long len = Long.valueOf(result).longValue();
			Log.d("System.out", "FW size=" + len);
			if (len != length) {
				return true;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		}
		return true;
    }
    
    public void showUpdateBtn() {
    	System.out.println("show===============update");
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
            Log.d(WiFiDirectActivity.TAG, e.toString());
            return false;
        }
        return true;
    }

}
