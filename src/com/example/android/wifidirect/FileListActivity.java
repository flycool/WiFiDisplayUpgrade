package com.example.android.wifidirect;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

import com.example.android.util.ContinueFTP;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
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
	private String deviceIp;
	
	public static final int SHOW_PROGRESS_DIALOG = 1;
	public static final int TRANSFER_PROGRESS = 2;
	public static final int SHOW_MESSAGE = 3;
	
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
			case SHOW_MESSAGE:
				String message = (String)msg.obj;
				Toast.makeText(FileListActivity.this, message, Toast.LENGTH_LONG).show();
				break;
			}
    	};
    };
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.file_list);
		
		deviceIp = getIntent().getStringExtra("device_ip");
		
		adapter = new FileAdapter(this);
		setListAdapter(adapter);
		adapter.scanFiles(rootDir);
		getListView().setOnItemClickListener(this);
		
		filePath = (TextView) findViewById(R.id.filepath);
		filePath.setText(rootDir);
		upButton = (Button) findViewById(R.id.up);
		upButton.setText(getString(R.string.up));
		upButton.setOnClickListener(this);
		
		progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setTitle(getString(R.string.progeress_title));
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
		
		if (file.isDirectory()) {
			filePath.setText(file.getPath());
			adapter.scanFiles(file.getPath());
		} else {
			new AlertDialog.Builder(this)
				.setTitle(getString(R.string.progeress_title))
				.setMessage(getString(R.string.progeress_title) + " "+ fileName + "?")
				.setIcon(R.drawable.ic_action_on_off)
				.setPositiveButton(getString(R.string.progeress_title), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						new Thread(new Runnable(){@Override
							public void run() {
								uploadFile(deviceIp, fileName);
							}}).start();
					}
				})
				.setNegativeButton(getString(R.string.cancel), null)
				.create().show();
		}
	}
	
	private void uploadFile(String deviceIp, String fileName) {
		ContinueFTP ftpClient = new ContinueFTP(this);
		try {
			boolean result = ftpClient.connect(deviceIp, 3721, ContinueFTP.USERNAME, ContinueFTP.PASSWORD);
			if (result) {
				String remote = fileName;
				String local = currentPath + "/" + fileName;
				
//				ftpClient.download(remote, local);
				String uploadResult = ftpClient.upload(local, remote, mHandler);
				Log.d("FTP", "upload result : " + uploadResult);
				if (uploadResult.equals("File_Exists") || uploadResult.equals("Remote_Bigger_Local")) {
					showMessage("File exists");
					return;
				}
				progressDialog.dismiss();
				if (uploadResult.equals("Upload_From_Break_Success") || uploadResult.equals("Upload_New_File_Success")) {
					showMessage(fileName + " " + getString(R.string.upload_success));
					FileListActivity.this.finish();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void showMessage(String message) {
		Message msg = mHandler.obtainMessage();
		msg.obj = message;
		msg.what = SHOW_MESSAGE;
		mHandler.sendMessage(msg);
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
			dealWithIcon(icon, f);
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
		
		private void dealWithIcon(ImageView image, File f) {
			int resId = 0;
			if (f.isDirectory()) {
				resId = R.drawable.folder;
			} else {
				String suffix = f.getName().substring(f.getName().lastIndexOf(".") + 1);
				if (suffix.equals("apk")) {
					showUninstallAPKIcon(image, f.getPath());
					return;
				} else if (suffix.equals("rar")) {
					resId = R.drawable.rar;
				} else {
					resId = R.drawable.file;
				}
			}
			image.setImageResource(resId);
		}
		
		private void getUninstallApkInfo(ImageView image, Context context, String archiveFilePath) {
			PackageManager pm = context.getPackageManager();
			PackageInfo info = pm.getPackageArchiveInfo(archiveFilePath, PackageManager.GET_ACTIVITIES);
			if (info != null) {
				ApplicationInfo appInfo = info.applicationInfo;
				Drawable icon = pm.getApplicationIcon(appInfo);
				image.setImageDrawable(icon);
			}
		}
		
		/**
		 * 获取未安装apk的图标 ;-)
		 * @param image
		 * @param apkPath
		 */
		private void showUninstallAPKIcon(ImageView image, String apkPath) {
	        String PATH_PackageParser = "android.content.pm.PackageParser";
	        String PATH_AssetManager = "android.content.res.AssetManager";
	        try {
	            // apk包的文件路径
	            // 这是一个Package 解释器, 是隐藏的
	            // 构造函数的参数只有一个, apk文件的路径
	            // PackageParser packageParser = new PackageParser(apkPath);
	            Class pkgParserCls = Class.forName(PATH_PackageParser);
	            Class[] typeArgs = new Class[1];
	            typeArgs[0] = String.class;
	            Constructor pkgParserCt = pkgParserCls.getConstructor(typeArgs);
	            Object[] valueArgs = new Object[1];
	            valueArgs[0] = apkPath;
	            Object pkgParser = pkgParserCt.newInstance(valueArgs);
	            Log.d("ANDROID_LAB", "pkgParser:" + pkgParser.toString());
	            // 这个是与显示有关的, 里面涉及到一些像素显示等等, 我们使用默认的情况
	            DisplayMetrics metrics = new DisplayMetrics();
	            metrics.setToDefaults();
	            
	            // PackageParser.Package mPkgInfo =
	            // 			packageParser.parsePackage(new File(apkPath), apkPath, metrics, 0);  
	            typeArgs = new Class[4];
	            typeArgs[0] = File.class;
	            typeArgs[1] = String.class;
	            typeArgs[2] = DisplayMetrics.class;
	            typeArgs[3] = Integer.TYPE;
	            Method pkgParser_parsePackageMtd = pkgParserCls.getDeclaredMethod("parsePackage", typeArgs);
	            valueArgs = new Object[4];
	            valueArgs[0] = new File(apkPath);  
	            valueArgs[1] = apkPath;
	            valueArgs[2] = metrics;
	            valueArgs[3] = 0;
	            Object pkgParserPkg = pkgParser_parsePackageMtd.invoke(pkgParser, valueArgs);
	            
	            // 应用程序信息包, 这个公开的, 不过有些函数, 变量没公开 
	            // ApplicationInfo info = mPkgInfo.applicationInfo;
	            Field appInfoFld = pkgParserPkg.getClass().getDeclaredField("applicationInfo");
	            ApplicationInfo info = (ApplicationInfo) appInfoFld.get(pkgParserPkg);
	            // uid 输出为"-1"，原因是未安装，系统未分配其Uid。
	            Log.d("ANDROID_LAB", "pkg:" + info.packageName + " uid=" + info.uid);
	            
	            // Resources pRes = getResources();
	            // AssetManager assmgr = new AssetManager();
	            // assmgr.addAssetPath(apkPath);
	            // Resources res = new Resources(assmgr, pRes.getDisplayMetrics(), pRes.getConfiguration());
	            Class assetMagCls = Class.forName(PATH_AssetManager);
	            Constructor assetMagCt = assetMagCls.getConstructor((Class[]) null);
	            Object assetMag = assetMagCt.newInstance((Object[]) null);
	            typeArgs = new Class[1];
	            typeArgs[0] = String.class;
	            Method assetMag_addAssetPathMtd = assetMagCls.getDeclaredMethod("addAssetPath",  typeArgs);
	            valueArgs = new Object[1];
	            valueArgs[0] = apkPath;
	            assetMag_addAssetPathMtd.invoke(assetMag, valueArgs);
	            Resources res = getResources();
	            typeArgs = new Class[3];
	            typeArgs[0] = assetMag.getClass();
	            typeArgs[1] = res.getDisplayMetrics().getClass();
	            typeArgs[2] = res.getConfiguration().getClass();
	            Constructor resCt = Resources.class.getConstructor(typeArgs);
	            valueArgs = new Object[3];
	            valueArgs[0] = assetMag;
	            valueArgs[1] = res.getDisplayMetrics();
	            valueArgs[2] = res.getConfiguration();
	            res = (Resources) resCt.newInstance(valueArgs);
	            
	            CharSequence label = null;
	            if (info.labelRes != 0) {
	                label = res.getText(info.labelRes);
	            }
	            // if (label == null) {
	            // label = (info.nonLocalizedLabel != null) ? info.nonLocalizedLabel
	            // : info.packageName;
	            // }
	            Log.d("ANDROID_LAB", "label=" + label);
	            
	            // 这里就是读取一个apk程序的图标
	            if (info.icon != 0) {
	                Drawable icon = res.getDrawable(info.icon);
	                image.setImageDrawable(icon);
	            }
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
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
