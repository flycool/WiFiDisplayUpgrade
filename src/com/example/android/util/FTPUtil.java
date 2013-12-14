package com.example.android.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.SocketException;
import java.util.Date;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import android.util.Log;

public class FTPUtil {
	private static final String TAG = "FTPUtil";

	private static final String USERNAME = "a";
	private static final String PASSWORD = "a";

	private static final String FtpRootDir = "/sdcard";

	public static void downloadFile(String hostname, int port, String saveDir,
			String fileName) {
		FTPClient ftp = new FTPClient();
		int reply;
		try {
			ftp.connect(hostname, port);
			ftp.login(USERNAME, PASSWORD);
			reply = ftp.getReplyCode();
			if (!FTPReply.isPositiveCompletion(reply)) {
				ftp.disconnect();
				return;
			}

			ftp.changeWorkingDirectory(saveDir);
			FTPFile[] files = ftp.listFiles();
			for (FTPFile f : files) {
				if (f.getName().equals(fileName)) {
					File localFile = new File(saveDir + "/" + f.getName());
					System.out.println("localfile path===========11======== " + localFile.getAbsolutePath());
					OutputStream os = new FileOutputStream(localFile);
					ftp.retrieveFile(f.getName(), os);
					os.close();
				}
			}
			ftp.logout();
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void uploadFile(String hostname, int port, String fileName, InputStream fileStream) {
		FTPClient ftp = new FTPClient();
		int reply;
		try {
			ftp.connect(hostname, port);
			ftp.login(USERNAME, PASSWORD);
			reply = ftp.getReplyCode();
			if (!FTPReply.isPositiveCompletion(reply)) {
				ftp.disconnect();
				return;
			}
			ftp.changeWorkingDirectory(FtpRootDir);
			ftp.setFileType(FTPClient.BINARY_FILE_TYPE);
			ftp.storeFile(fileName, fileStream);
			System.out.println("storeFile==========================");

			fileStream.close();
			ftp.logout();
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static String uploadFile(String remoteFile, File localFile, FTPClient ftpClient, long remoteSize) throws IOException {
		String status;
		// 显示进度的上传
		long step = localFile.length() / 100;
		long process = 0;
		long localreadbytes = 0L;
		RandomAccessFile raf = new RandomAccessFile(localFile, "r");
		 OutputStream out = ftpClient.appendFileStream(new String(remoteFile.getBytes("UTF-8"), "iso-8859-1"));
		// 断点续传
		if (remoteSize > 0) {
			ftpClient.setRestartOffset(remoteSize);
			process = remoteSize / step;
			raf.seek(remoteSize);
			localreadbytes = remoteSize;
		}

		byte[] bytes = new byte[1024];
		int c;
		while ((c = raf.read(bytes)) != -1) {
			out.write(bytes, 0, c);
			localreadbytes += c;

			if (localreadbytes / step != process) {
				process = localreadbytes / step;
				System.out.println("file:=== " + remoteFile + " proecss====== :" + process + "%," + localreadbytes / 1024 + " KB");
			}
		}
		try {
			out.flush();
			raf.close();
			out.close();
			boolean result = ftpClient.completePendingCommand();
			if (remoteSize > 0) {
				status = result ? "Upload_From_Break_Success" : "UploadStatus.Upload_From_Break_Failed";
			} else {
				status = result ? "Upload_New_File_Success" : "Upload_New_File_Failed";
			}
			return status;
		} catch (Exception e) {
			Log.d(TAG, e.getMessage());
			status = process < 100 ? "Remote_Time_Out" : "Remote_Time_Out_Over";
		}
		return status;
	}

	public static String streamToString(InputStream inputStream) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte buf[] = new byte[1024];
		int len;
		try {
			while ((len = inputStream.read(buf)) != -1) {
				bos.write(buf, 0, len);
			}
			bos.close();
			inputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

		return bos.toString();
	}

}
