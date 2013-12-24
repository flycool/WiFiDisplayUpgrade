package com.example.android.wifidirect;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * mutiple notification show the notification progress correctly
 * @author wzh
 *
 */
public class MutipleNotification implements Parcelable {
	
	private Context mContext;

	private int notificationId;
	private int oldId;
	private String fileName;
	
	private int process;
	private Handler mHandler;
	private ProgressDialog progressDialog;
	private NotificationManager nm;
	private boolean isDone;
	
	public static final int TASK_DONE = 5;
	
	public MutipleNotification(final Context context) {
		nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		mContext = context;
		mHandler = new Handler(context.getMainLooper()) {
			@Override
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case FileListActivity.SHOW_PROGRESS_DIALOG:
					//initProgressDialog(context);
					break;
	    		case FileListActivity.SHOW_NOTIFICATION:
					process = msg.arg1;
					if (progressDialog != null) {
						progressDialog.setProgress(process);
					}
					notificationId = msg.arg2;
					fileName = (String)msg.obj;
					if (oldId < notificationId) {
						showUploadNotification(notificationId);
						oldId = notificationId;
					}
					break;
	    		case TASK_DONE:
	    			isDone = true;
					break;
				}
			}
		};
	}
	
	private void showUploadNotification(final int notificationId) {
    	new Thread(new Runnable(){public void run() {
    		
    		Intent resultIntent = new Intent(mContext, WiFiDirectActivity.class);
    		PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, resultIntent, 0);
    		
    		final Notification.Builder mBuilder = new Notification.Builder(mContext);
    		mBuilder.setSmallIcon(R.drawable.upload)
            .setContentTitle("Upload File " + fileName)
            .setContentText("Upload in progress")
        	.setContentIntent(pendingIntent)
        	.setAutoCancel(true);
    		
    		int level = 0;
    		int count = 0;
    		while(true) {
				mBuilder.setProgress(100, process, false);
		    	nm.notify(notificationId, mBuilder.build());
		    	
		    	count++;
		    	if (count % 3 == 0) {
		    		level++;
					if (level == 3) level = 0;
					mBuilder.setSmallIcon(R.drawable.notification_view, level);
				}
		    	
		    	if (process >= 100 || isDone) {
		    		mBuilder.setContentText("Upload finish").setProgress(0, 0, false);
		    		nm.notify(notificationId, mBuilder.build());
		    		if (progressDialog != null && progressDialog.isShowing()) {
		    			progressDialog.dismiss();
		    		}
		    		break;
		    	}
		    	try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
		    	
    		}
    	};}).start();
	}
	
	private void initProgressDialog(Context context) {
		progressDialog = new ProgressDialog(context);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setTitle(context.getString(R.string.progeress_title));
        progressDialog.setCancelable(true);
        progressDialog.show();
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

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public boolean isDone() {
		return isDone;
	}

	public void setDone(boolean isDone) {
		this.isDone = isDone;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		
	}
	
}
