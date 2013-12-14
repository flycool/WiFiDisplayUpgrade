package com.example.android.wifidirect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.example.android.util.ContinueFTP;

import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;

public class FileListActivity extends ListActivity implements
		OnItemClickListener {

	private ArrayList<File> fileList = new ArrayList<File>();
	private String currentPath;
	ProgressDialog progressDialog = null;

	private Handler mHandler = new Handler() {
    	public void handleMessage(android.os.Message msg) {
    		int progress = msg.arg1;
    		progressDialog.setProgress(progress);
    		
    	};
    };
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.file_list);
		
		FileAdapter adapter = new FileAdapter(this);
		setListAdapter(adapter);
		adapter.scanFiles("/sdcard");
		getListView().setOnItemClickListener(this);
		
		progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setTitle("upload");
        progressDialog.setCancelable(true);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
		final String fileName = ((File)getListView().getAdapter().getItem(pos)).getName();
		Toast.makeText(this, fileName , Toast.LENGTH_LONG).show();
		
		progressDialog.show();
		new Thread(new Runnable(){@Override
		public void run() {
			uploadFile(fileName);
		}}).start();
	}
	
	private void uploadFile(String fileName) {
		ContinueFTP ftpClient = new ContinueFTP(this);
		try {
			boolean result = ftpClient.connect("192.168.1.136", 3721, "a", "a");
			if (result) {
				final String sdcarPath = Environment.getExternalStorageDirectory().getAbsolutePath(); 
				String remote = fileName;
				String local = sdcarPath + "/" + fileName;
				
//				ftpClient.download(remote, local);
				ftpClient.upload(local, remote, mHandler);
				progressDialog.dismiss();
				FileListActivity.this.finish();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private class FileAdapter extends BaseAdapter {

		private Context context;
		private ArrayList<File> files = new ArrayList<File>();

		public FileAdapter(Context context) {
			this.context = context;
		}

		@Override
		public int getCount() {
			return files.size();
		}

		@Override
		public Object getItem(int position) {
			return files.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				v = View.inflate(context, R.layout.file_item, null);
			}
			TextView fileName = (TextView) v.findViewById(R.id.name);
			TextView fileSize = (TextView) v.findViewById(R.id.size);
			ImageView icon = (ImageView) v.findViewById(R.id.image);
			File f = files.get(position);
			fileName.setText(f.getName());
			fileSize.setText(getFilesSize(f));
			dealWithIcon(f, icon);
			return v;
		}
		
		public void scanFiles(String path) {
			files.clear();
			File dir = new File(path);
			File[] subFiles = dir.listFiles();
			if (subFiles != null) {
				for (File f : subFiles) {
					files.add(f);
				}
				this.notifyDataSetChanged();
			}
			currentPath = path;
		}
		
		private void dealWithIcon(File f, ImageView icon) {
			if (f.isDirectory()) {
				icon.setImageResource(R.drawable.folder);
			} else {
				String suffix = f.getName().substring(f.getName().lastIndexOf(".") + 1);
				if (suffix.equals("apk")) {
					icon.setImageResource(R.drawable.apk);
				} else {
					icon.setImageResource(R.drawable.file);
				}
			}
		}
		
		private String getFilesSize(File f) {
			int sub_index = 0;
			String show = "";
			if (f.isFile()) {
				long length = f.length();
				if (length > 1024 * 1024) {
					show = length / 1024*1024 + "MB";
				} else {
					show = length / 1024 + "KB";
				}
				/*
				if (length >= 1073741824) {
					sub_index = String.valueOf((float) length / 1073741824).indexOf(".");
					show = ((float) length / 1073741824 + "000").substring(0, sub_index + 3) + "GB";
				} else if (length >= 1048576) {
					sub_index = (String.valueOf((float) length / 1048576)).indexOf(".");
					show = ((float) length / 1048576 + "000").substring(0, sub_index + 3) + "GB";
				} else if (length >= 1024) {
					sub_index = (String.valueOf((float) length / 1024)).indexOf(".");
					show = ((float) length / 1024 + "000").substring(0, sub_index + 3) + "GB";
				} else if (length < 1024)
					show = String.valueOf(length) + "B";*/
			}
			return show;
		}
	}
	
}
