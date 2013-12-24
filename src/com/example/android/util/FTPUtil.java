package com.example.android.util;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.ArrayList;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

public class FTPUtil {
	private static final String TAG = "FTPUtil";

	public static String streamToString(InputStream inputStream) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte buf[] = new byte[256];
		int ava = 0;
		try {
			while((ava = inputStream.available()) > 0) {
				inputStream.read(buf, 0, ava);
				bos.write(buf, 0, ava);
			}
			bos.close();
			inputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return bos.toString();
	}
	
	public static String parseStreamContent(InputStream ins, int index, String regex) {
		ArrayList<String> contents = new ArrayList<String>();
		BufferedReader br = new BufferedReader(new InputStreamReader(ins));
		String line = "";
		try {
			while((line = br.readLine()) != null) {
				contents.add(line);
			}
			String content = contents.get(index);
			String result = content.substring(content.lastIndexOf(regex) + 1);
			return result;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static int parseFWVersion(String version) {
		//07.00.121316U
		String v = version;
		v = v.substring(0, v.lastIndexOf("U")).replace(".", "");
		return Integer.valueOf(v).intValue();
	}

}
