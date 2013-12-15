package com.example.android.wifidirect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.example.android.util.ContinueFTP;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;

public class FileListActivity extends ListActivity implements
		OnItemClickListener, OnClickListener {

	private String currentPath;
	ProgressDialog progressDialog = null;
	private String rootDir = Environment.getExternalStorageDirectory().getPath();
	private FileAdapter adapter = null;
	
	private TextView filePath;
	private Button upButton;
	
	public static final int SHOW_PROGRESS_DIALOG = 1;
	public static final int TRANSFER_PROGRESS = 2;
	
	private Handler mHandler = new Handler() {
    	public void handleMessage(android.os.Message msg) {
    		switch (msg.what) {
			case SHOW_PROGRESS_DIALOG:
				progressDialog.show();
				break;
			case TRANSFER_PROGRESS:
				int progress = msg.arg1;
				progressDialog.setProgress(progress);
				break;
			}
    	};
    };
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.file_list);
		
		adapter = new FileAdapter(this);
		setListAdapter(adapter);
		adapter.scanFiles(rootDir);
		getListView().setOnItemClickListener(this);
		
		filePath = (TextView) findViewById(R.id.filepath);
		filePath.setText(rootDir);
		upButton = (Button) findViewById(R.id.up);
		upButton.setText("UP");
		upButton.setOnClickListener(this);
		
		progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setTitle("upload");
        progressDialog.setCancelable(true);
	}

	@Override
	public void onClick(View v) {
		if (currentPath.equals(rootDir)) {
			adapter.scanFiles(currentPath);
			return;
		}
		File file = new File(currentPath);
		filePath.setText(file.getParent());
		adapter.scanFiles(file.getParent());
	}
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
		File file = (File) adapter.getItem(pos);
		final String fileName = file.getName();
		Toast.makeText(this, fileName , Toast.LENGTH_LONG).show();
		
		if (file.isDirectory()) {
			filePath.setText(file.getPath());
			adapter.scanFiles(file.getPath());
		} else {
			new AlertDialog.Builder(this)
				.setTitle("Upload")
				.setMessage("Upload?")
				.setIcon(R.drawable.ic_action_on_off)
				.setPositiveButton("Upload", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						new Thread(new Runnable(){@Override
							public void run() {
								uploadFile(fileName);
							}}).start();
					}
				})
				.setNegativeButton("cancel", null)
				.create().show();
		}
	}
	
	private void uploadFile(String fileName) {
		ContinueFTP ftpClient = new ContinueFTP(this);
		try {
			boolean result = ftpClient.connect("192.168.1.136", 3721, "a", "a");
			if (result) {
				mHandler.sendEmptyMessage(SHOW_PROGRESS_DIALOG);
				final String sdcarPath = Environment.getExternalStorageDirectory().getAbsolutePath(); 
				String remote = fileName;
				String local = sdcarPath + "/" + fileName;
//				ftpClient.download(remote, local);
				ftpClient.upload(local, remote, mHandler);
				progressDialog.dismiss();
				FileListActivity.this.finish();
			}
		} catch (Exception e) {
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
			icon.setImageResource(dealWithIcon(f));
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
			}
			this.notifyDataSetChanged();
			currentPath = path;
		}
		
		private int dealWithIcon(File f) {
			int resId;
			if (f.isDirectory()) {
				resId = R.drawable.folder;
			} else {
				String suffix = f.getName().substring(f.getName().lastIndexOf(".") + 1);
				if (suffix.equals("apk")) {
					resId = R.drawable.apk;
				} else {
					resId = R.drawable.file;
				}
			}
			return resId;
		}
		
		private String getFilesSize(File f) {
			int sub_index = 0;
			String show = "";
			if (f.isFile()) {
				long length = f.length();
				if (length >= 1073741824) {
					sub_index = String.valueOf((float) length / 1073741824).indexOf(".");
					show = ((float) length / 1073741824 + "000").substring(0, sub_index + 3) + "GB";
				} else if (length >= 1048576) {
					sub_index = (String.valueOf((float) length / 1048576)).indexOf(".");
					show = ((float) length / 1048576 + "000").substring(0, sub_index + 3) + "MB";
				} else if (length >= 1024) {
					sub_index = (String.valueOf((float) length / 1024)).indexOf(".");
					show = ((float) length / 1024 + "000").substring(0, sub_index + 3) + "KB";
				} else if (length < 1024)
					show = String.valueOf(length) + "B";
			}
			return show;
		}
	}
}
