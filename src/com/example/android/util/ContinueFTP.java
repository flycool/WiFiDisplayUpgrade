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

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

public class ContinueFTP {
	private static final String TAG = "ContinueFTP";
	
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
		
		long remoteSize = files[0].getSize();
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
			long step = remoteSize/100;
			long process = localSize/step;
			while((len = ins.read(buffer)) != -1) {
				fos.write(buffer, 0, len);
				localSize += len;
				long nowProcess = localSize/step;
				if (nowProcess > process) {
					process = nowProcess;
					Log.e(TAG, "localFile: " + f.getAbsolutePath() + "download process: " + process + "%, " + localSize/1024 + "KB");
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
			long step = remoteSize/100;
			long process = 0;
			long localSize = 0l;
			while((len = in.read(buf)) != -1) {
				out.write(buf, 0, len);
				localSize += len;
				long nowProcess = localSize/step;
				if (nowProcess > process) {
					process = nowProcess;
					Log.e(TAG, "localFile: " + f.getAbsolutePath() + "download process: " + process + "%, " + localSize/1024 + "KB");
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
			//create directors
			
		}
		
		FTPFile[] files = ftpClient.listFiles(new String(remoteFileName.getBytes("UTF-8"), "iso-8859-1"));
		File f = new File(local);
		if (files.length == 1) {
			long remoteSize = files[0].getSize();
			long localSize = f.length();
			if (remoteSize == localSize) {
				return "File_Exits";
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
		String result = null;
		long step = localFile.length()/100;
		long process = 0;
		long localReadBytes = 0L;
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
				Log.e(TAG, "remoteFile: " + remoteFile + "upload process: " + process + "%, " + localReadBytes/1024 + "KB");
				Message msg = handler.obtainMessage();
				msg.arg1 = new Long(process).intValue();
				handler.sendMessage(msg);
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
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}
