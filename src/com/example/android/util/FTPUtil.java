package com.example.android.util;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class FTPUtil {

	public static String streamToString(InputStream inputStream) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte buf[] = new byte[256];
		int ava = 0;
		try {
			while((ava = inputStream.available()) > 0) {
				inputStream.read(buf, 0, ava);
				bos.write(buf, 0, ava);
			}
			bos.flush();
			inputStream.close();
			bos.close();
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
	
	public static long parseFWVersion(String version) {
		//07.00.121316U
		long ret;
		String v = version;
		try {
			StringBuilder sb = new StringBuilder();
			for (int i=0; i<version.length(); i++) {
				char c = v.charAt(i);
				int ci = (int)v.charAt(i);
				if ((ci >= 48 && ci <= 57)) {
					sb.append(c);
				}
			}
			ret = Long.valueOf(sb.toString()).longValue();
		} catch (Exception e) {
			return 0;
		}
		return ret;
	}

}
