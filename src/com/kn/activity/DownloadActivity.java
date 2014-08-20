package com.kn.activity;

import java.io.File;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.kn.R;
import com.kn.service.DownloadProgressListener;
import com.kn.service.FileDownloader;

public class DownloadActivity extends Activity {

	private static final String TAG = "DownloadActivity";
	
	private static final int PROCESSING = 1;
	private static final int FAILURE = -1;
	private EditText pathText;
	private TextView resultView;
	private Button btn_download;
	private Button btn_stop;
	private ProgressBar progressBar;

	private Handler handler = new Handler() {
		
		public void handleMessage(Message message) {
			switch (message.what) {
			case PROCESSING:
				int size = message.getData().getInt("size");
				Log.i(TAG, "当前下载进度：" + size);
				progressBar.setProgress(size);
				float num = (float) progressBar.getProgress()
						/ (float) progressBar.getMax();
				int result = (int) (num * 100);
				resultView.setText(result + "%");
				if (progressBar.getProgress() == progressBar.getMax()) {
					btn_download.setEnabled(true);
					btn_stop.setEnabled(false);
					Toast.makeText(getApplicationContext(), R.string.success,
							Toast.LENGTH_LONG).show();
				}
				break;
			case FAILURE:
				Toast.makeText(getApplicationContext(), R.string.error,
						Toast.LENGTH_LONG).show();
				break;
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.download_main);

		pathText = (EditText) findViewById(R.id.path);
		resultView = (TextView) findViewById(R.id.resultView);
		btn_download = (Button) findViewById(R.id.btn_download);
		btn_stop = (Button) findViewById(R.id.btn_stop);
		progressBar = (ProgressBar) findViewById(R.id.progressBar);
		progressBar.setVisibility(View.VISIBLE);
		ButtonClickListener listener = new ButtonClickListener();

		btn_download.setOnClickListener(listener);
		btn_stop.setOnClickListener(listener);
	}

	private class ButtonClickListener implements View.OnClickListener {

		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.btn_download:
				String path = pathText.getText().toString();
				if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
					File saveDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
//					getExternalFilesDirs(Environment.DIRECTORY_MOVIES);
					Log.i(TAG, "保存路径：" + saveDir.getAbsolutePath());
					download(path, saveDir);
				} else {
					Toast.makeText(getApplicationContext(), R.string.sdcarderror, Toast.LENGTH_LONG).show();
				}
				btn_download.setEnabled(false);
				btn_stop.setEnabled(true);
				break;
			case R.id.btn_stop:
				exit();
				btn_download.setEnabled(true);
				btn_stop.setEnabled(false);
				break;
			default:
				break;
			}
		}
		
		private DownloadTask task;
		
		public void exit() {
			if (task != null)
				task.exit();
		}
		
		private void download(String path, File saveDir) {
			task = new DownloadTask(path, saveDir);
			new Thread(task).start();
		}

		private final class DownloadTask implements Runnable {
			private String path;
			private File saveDir;
			private FileDownloader loader;
			
			public DownloadTask(String path, File saveDir) {
				this.path = path;
				this.saveDir = saveDir;
			}
			
			public void exit() {
				if (loader != null) 
					loader.exit();
			}
			
			DownloadProgressListener downloadProgressListener = new DownloadProgressListener() {
				public void onDownloadSize(int size) {
					Message message = new Message();
					message.what = PROCESSING;
					message.getData().putInt("size", size);
					handler.sendMessage(message);
				}
			};
			
			@Override
			public void run() {
				try {
					loader = new FileDownloader(getApplicationContext(), path, saveDir, 3);
					Log.i(TAG, "进度条阀值：" + loader.getFileSize());
					progressBar.setMax(loader.getFileSize());
					loader.download(downloadProgressListener);
				} catch (Exception e) {
					e.printStackTrace();
					handler.sendMessage(handler.obtainMessage(FAILURE));
					
//					Message message = handler.obtainMessage();
//					message.what = FAILURE;
				}
			}
		}
	}
}
