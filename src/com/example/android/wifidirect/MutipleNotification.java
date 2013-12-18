package com.example.android.wifidirect;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;

public class MutipleNotification {
	
	private Context mContext;

	private int notificationId;
	private int oldId;
	
	private int process;
	
	private Handler mHandler;
	
	private NotificationManager nm;
	
	public MutipleNotification(Context context) {
		nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
	
		mContext = context;
		mHandler = new Handler(context.getMainLooper()){
			@Override
			public void handleMessage(Message msg) {
				switch (msg.what) {
	    		case FileListActivity.SHOW_NOTIFICATION:
					process = msg.arg1;
					notificationId = msg.arg2;
					if (oldId < notificationId) {
						showUploadNotification(notificationId);
						oldId = notificationId;
					}
					break;
				}
			}
		};
	}
	
	private void showUploadNotification(final int notificationId) {
    	new Thread(new Runnable(){public void run() {
    		final Notification.Builder mBuilder = new Notification.Builder(mContext);
    		mBuilder.setSmallIcon(R.drawable.upload)
            .setContentTitle("Upload File " + notificationId)
            .setContentText("Upload in progress");
        	Intent resultIntent = new Intent(mContext, WiFiDirectActivity.class);
        	PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, resultIntent, 0);
        	mBuilder.setContentIntent(pendingIntent);
        	mBuilder.setAutoCancel(true);
    		while(true) {
				mBuilder.setProgress(100, process, false);
				if (process >= 100) {
					mBuilder.setContentText("Upload finish").setProgress(0, 0, false);
					nm.notify(notificationId, mBuilder.build());
					break;
				}
		    	nm.notify(notificationId, mBuilder.build());
		    	try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
		    	
    		}
    	};}).start();
	}
	
	
	public int getNotificationId() {
		return notificationId;
	}

	public void setNotificationId(int notificationId) {
		this.notificationId = notificationId;
	}
	
	public int getProcess() {
		return process;
	}

	public void setProcess(int process) {
		this.process = process;
	}

	public Handler getmHandler() {
		return mHandler;
	}

	public void setmHandler(Handler mHandler) {
		this.mHandler = mHandler;
	}

	
}
