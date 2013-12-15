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

			fileStream.close();
			ftp.logout();
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
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
