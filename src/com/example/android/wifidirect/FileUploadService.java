package com.example.android.wifidirect;


import java.io.File;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.example.android.util.ContinueFTP;

public class FileUploadService extends Service {
	private Looper mServiceLooper;
	private ServiceHandler mServiceHandler;
	
	private int countThread;
	private SparseArray<Handler> map = new SparseArray<Handler>();
	private SparseArray<MutipleNotification> mMutipleNotification = new SparseArray<MutipleNotification>();
	
	public FileUploadService() {
	}
	
	private final class ServiceHandler extends Handler {
		public ServiceHandler(Looper looper) {
			super(looper);
		}
		@Override
		public void handleMessage(Message msg) {
			Bundle bundle = msg.getData();
			
			String deviceIp = bundle.getString("deviceIp");
			String fileNamePath = bundle.getString("path");
			PendingIntent receiver = bundle.getParcelable("receiver");
			
			countThread++;
			MutipleNotification mNotification  = new MutipleNotification(DeviceDetailFragment.instance);
			map.put(countThread, mNotification.getmHandler());
			mMutipleNotification.put(countThread, mNotification);
			uploadFile(deviceIp, fileNamePath, countThread, map);
			
			try {
				receiver.send(getApplicationContext(), 0, null);
			} catch (CanceledException e) {
				e.printStackTrace();
			}
			
			stopSelf(msg.arg1);
		}
	}
	
	private void uploadFile(String deviceIp, String fileName, int count, SparseArray<Handler> map) {
//		ContinueFTP ftpClient = new ContinueFTP(this);
		ContinueFTP ftpClient = DeviceDetailFragment.ftp;
		try {
			//boolean result = ftpClient.connect(deviceIp, ContinueFTP.PORT, ContinueFTP.USERNAME, ContinueFTP.PASSWORD);
			//if (result) {
				String remote = fileName;
				remote = remote.substring(remote.lastIndexOf("/")+1);
				if (remote.equals("install.img")) {
					remote = "/fw";
				}
				String local = fileName;
				String uploadResult = ftpClient.upload(local, remote, count, map);
				Log.d("System.out", "upload result : " + uploadResult);
				if (uploadResult.equals("File_Exists") || uploadResult.equals("Remote_Bigger_Local")) {
					//showMessage("File exists");
					return;
				}
				if (uploadResult.equals("Upload_From_Break_Success") || uploadResult.equals("Upload_New_File_Success")) {
					//showMessage(fileName + " " + getString(R.string.upload_success));
					
					SharedPreferences uploadFlag = getSharedPreferences("upload_process", 0);
					SharedPreferences.Editor editor = uploadFlag.edit();
					editor.putInt("upload_done", 1);
					editor.putLong("file_length", new File(local).length());
					editor.commit();
					
					
					mMutipleNotification.remove(countThread);
					--countThread;
					if (countThread == 0) {
						
					}
				}
			//}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void onCreate() {
		HandlerThread thread = new HandlerThread("uploadFile", Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();
		
		mServiceLooper = thread.getLooper();
		mServiceHandler = new ServiceHandler(mServiceLooper);
	}
	
	@Override
	public int onStartCommand(final Intent intent, int flags, final int startId) {
		Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();
		
		Bundle bundle = intent.getExtras();
		Message msg = mServiceHandler.obtainMessage();
		msg.arg1 = startId;
		msg.setData(bundle);
		mServiceHandler.sendMessage(msg);
		
		return START_STICKY;
	}
	
	@Override
	public void onDestroy() {
		Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show(); 
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

}
