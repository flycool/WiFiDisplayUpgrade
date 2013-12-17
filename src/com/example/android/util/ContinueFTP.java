package com.example.android.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import com.example.android.wifidirect.FileListActivity;
import com.example.android.wifidirect.R;
import com.example.android.wifidirect.WiFiDirectActivity;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class ContinueFTP {
	private static final String TAG = "ContinueFTP";
	public static final String USERNAME = "a";
	public static final String PASSWORD = "a";
	
	FTPClient ftpClient;
	Context context;
	
	public ContinueFTP(Context context) {
		this.context = context;
		ftpClient = new FTPClient();
		//设置将过程中使用到的命令输出到控制台
		ftpClient.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));
	}
	
	 /** *//** 
     * 连接到FTP服务器 
     * @param hostname 主机名 
     * @param port 端口 
     * @param username 用户名 
     * @param password 密码 
     * @return 是否连接成功 
     * @throws IOException 
     */  
	public boolean connect(String hostname, int port, String username, String password) throws IOException {
		ftpClient.connect(hostname, port);
		ftpClient.setControlEncoding("UTF-8");
		if (FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
			if (ftpClient.login(username, password));
				return true;
		}
		disconnect();
		return false;
	}
	
	public void disconnect() throws IOException {
		if (ftpClient.isConnected()) {
			ftpClient.disconnect();
		}
	}
	
	/** *//** 
     * 从FTP服务器上下载文件,支持断点续传，上传百分比汇报 
     * @param remote 远程文件路径 
     * @param local 本地文件路径 
     * @return 上传的状态 
     * @throws IOException 
     */  
	public String download(String remote, String local) throws IOException {
		ftpClient.enterLocalPassiveMode();
		ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
		String result = null;
		
		FTPFile[] files = ftpClient.listFiles(new String(remote.getBytes("UTF-8"), "iso-8859-1"));
		if (files.length != 1) {
			Log.e(TAG, "remote file not exists");
			return "Remote_File_Noexist";
		}
		
		float remoteSize = files[0].getSize();
		File f = new File(local);
		if (f.exists()) {
			long localSize = f.length();
			if (localSize >= remoteSize) {
				Log.e(TAG, "local file bigger remote file, download terminal");
				return "Local_Bigger_Remote";
			}
			
			FileOutputStream fos = new FileOutputStream(f, true);
			ftpClient.setRestartOffset(localSize);
			InputStream ins = ftpClient.retrieveFileStream(new String(remote.getBytes("UTF-8"), "iso-8859-1"));
			byte[] buffer = new byte[1024];
			int len;
			float step = remoteSize/100;
			float process = localSize/step;
			while((len = ins.read(buffer)) != -1) {
				fos.write(buffer, 0, len);
				localSize += len;
				float nowProcess = localSize/step;
				if (nowProcess > process) {
					process = nowProcess;
					//Log.d(TAG, "localFile: " + f.getAbsolutePath() + "download process: " + process + "%, " + localSize/1024 + "KB");
				}
			}
			ins.close();
			fos.close();
			boolean isDone = ftpClient.completePendingCommand();
			if (isDone)
				result = "Download_From_Break_Success";
			else 
				result = "Download_From_Break_Failed";
			
		}
		// file not exists
		else {
			FileOutputStream out = new FileOutputStream(f);
			InputStream in = ftpClient.retrieveFileStream(new String(remote.getBytes("UTF-8"), "iso-8859-1"));
			byte[] buf = new byte[1024];
			int len;
			float step = remoteSize/100;
			float process = 0;
			long localSize = 0;
			while((len = in.read(buf)) != -1) {
				out.write(buf, 0, len);
				localSize += len;
				float nowProcess = localSize/step;
				if (nowProcess > process) {
					process = nowProcess;
					//Log.d(TAG, "localFile: " + f.getAbsolutePath() + "download process: " + process + "%, " + localSize/1024 + "KB");
				}
			}
			in.close();
			out.close();
			boolean upNewStatus = ftpClient.completePendingCommand();
			if (upNewStatus)
				result = "Download_New_Success";
			else 
				result = "Download_New_Failed";
		}
		return result;
	}
	
	/** *//** 
     * 上传文件到FTP服务器，支持断点续传 
     * @param local 本地文件名称，绝对路径 
     * @param remote 远程文件路径，支持多级目录嵌套，支持递归创建不存在的目录结构 
     * @return 上传结果 
     * @throws IOException 
     */  
	public String upload(String local, String remote, Handler handler) throws IOException {
		String result = null;
		ftpClient.enterLocalPassiveMode();
		ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
		
		String remoteFileName = remote;
		if (remote.contains("/")) {
			remoteFileName = remote.substring(remote.lastIndexOf("/") + 1);
			// create directors
			if (!createDirectory(remote, ftpClient)) {
				return "Create_Directory_Fail";
			}
		}
		
		FTPFile[] files = ftpClient.listFiles(new String(remoteFileName.getBytes("UTF-8"), "iso-8859-1"));
		File f = new File(local);
		if (files.length == 1) {
			long remoteSize = files[0].getSize();
			long localSize = f.length();
			if (remoteSize == localSize) {
				return "File_Exists";
			} else if (remoteSize > localSize) {
				return "Remote_Bigger_Local";
			}
			result = uploadFile(remoteFileName, f, ftpClient, remoteSize, handler);
			// if failed delete and reupload
			if (result.equals("Upload_From_Break_Failed")) {
				if (!ftpClient.deleteFile(remoteFileName)) {
					return "Delete_Remote_Faild";
				}
				result = uploadFile(remoteFileName, f, ftpClient, 0, handler); 
			}
		} else {
			result = uploadFile(remoteFileName, f, ftpClient, 0, handler); 
		}
		return result;
	}
	
	public String uploadFile(String remoteFile, File localFile, FTPClient ftpClient, long remoteSize, Handler handler) throws IOException {
		handler.sendEmptyMessage(FileListActivity.SHOW_PROGRESS_DIALOG);
//		handler.sendEmptyMessage(FileListActivity.SHOW_NOTIFICATION);
		
		String result = null;
		float step = (float)localFile.length()/100;
		float process = 0;
		long localReadBytes = 0;
		RandomAccessFile raf = new RandomAccessFile(localFile, "r");
		OutputStream out = ftpClient.appendFileStream(new String(remoteFile.getBytes("UTF-8"), "iso-8859-1"));
		
		if (remoteSize > 0) {
			ftpClient.setRestartOffset(remoteSize);
			process = remoteSize/step;
			raf.seek(remoteSize);
			localReadBytes = remoteSize;
		}
		
		byte[] buffer = new byte[1024];
		int len;
		while((len = raf.read(buffer)) != -1) {
			out.write(buffer, 0, len);
			localReadBytes += len;
			if (localReadBytes/step != process) {
				process = localReadBytes/step;
				//Log.d(TAG, "remoteFile: " + remoteFile + "upload process: " + process + "%, " + localReadBytes/1024 + "KB");
				/*Message msg = handler.obtainMessage();
				msg.what = FileListActivity.TRANSFER_PROGRESS;
				msg.arg1 = new Float(process).intValue();
				handler.sendMessage(msg);*/
				
				Message msg2 = handler.obtainMessage();
				msg2.what = FileListActivity.SHOW_NOTIFICATION;
				msg2.arg2 = new Float(process).intValue();
				handler.sendMessage(msg2);
				
				showNotification(new Float(process).intValue());
			}
		}
		out.flush();
		raf.close();
		out.close();
		boolean status = ftpClient.completePendingCommand();
		if (remoteSize > 0) {
			result = status ? "Upload_From_Break_Success" : "Upload_From_Break_Failed";
		} else {
			result = status ? "Upload_New_File_Success" : "Upload_New_File_Failed";   
		}
		return result;
	}
	
	private void showNotification(int process) {
		final NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
    	final Notification.Builder mBuilder = new Notification.Builder(context);
    	mBuilder.setSmallIcon(R.drawable.upload)
        .setContentTitle("Upload File")
        .setContentText("Upload in progress");
    	Intent resultIntent = new Intent(context, WiFiDirectActivity.class);
    	PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, resultIntent, 0);
    	mBuilder.setContentIntent(pendingIntent);
    	
    	mBuilder.setProgress(100, process, false);
    	if (process >= 100) {
    		mBuilder.setContentText("Upload finish").setProgress(0, 0, false);
    	}
		nm.notify(0, mBuilder.build());
	}
	
	public boolean createDirectory(String remote, FTPClient ftpClient) throws IOException {
		String directory = remote.substring(0, remote.lastIndexOf("/")+1);
		if (!directory.equalsIgnoreCase("/") &&
				!ftpClient.changeWorkingDirectory(new String(directory.getBytes("UTF-8"),"iso-8859-1"))) {
			int start = 0;
			int end = 0;
			if (directory.startsWith("/")) {
				start = 1;
			} else {
				start = 0;
			}
			end = directory.indexOf("/", start);
			while(true) {
				String subDir = new String(remote.substring(start, end).getBytes("UTF-8"),"iso-8859-1");
				if (!ftpClient.changeWorkingDirectory(subDir)) {
					if (ftpClient.makeDirectory(subDir)) {
						ftpClient.changeWorkingDirectory(subDir);
					} else {
						return false;
					}
				}
				start = end + 1;
				end = directory.indexOf("/", start);
				if (end <= start) {
					break;
				}
			}
		}
		return true;
	}
	
}
