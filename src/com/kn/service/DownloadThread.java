package com.kn.service;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

import android.util.Log;

public class DownloadThread extends Thread {

	private static final String TAG = "DownloadThread";
	private File saveFile;
	private URL downUrl;
	private int block;
	private int threadId = -1;
	private int downloadedLength;
	private boolean finished = false;
	private FileDownloader downloader;

	public DownloadThread(FileDownloader downloader, URL downUrl,
			File saveFile, int block, int downloadedLength, int threadId) {
		this.downloader = downloader;
		this.downUrl = downUrl;
		this.saveFile = saveFile;
		this.block = block;
		this.downloadedLength = downloadedLength;
		this.threadId = threadId;
	}

	@Override
	public void run() {
		if (downloadedLength < block) {
			try {
				HttpURLConnection conn = (HttpURLConnection) downUrl
						.openConnection();
				conn.setConnectTimeout(5 * 1000);
				conn.setRequestMethod("GET");
				conn.setRequestProperty(
						"Accept",
						"image/gif, image/jpeg, image/pjpeg, application/x-shockwave-flash, application/xaml+xml, application/vnd.ms-xpsdocument, application/x-ms-xbap, application/x-ms-application, application/vnd.ms-excel, application/vnd.ms-powerpoint, application/msword, */*");
				conn.setRequestProperty("Accept-Language", "zh-CN");
				conn.setRequestProperty("Referer", downUrl.toString());
				conn.setRequestProperty("Charset", "UTF-8");
				int startPos = block * (threadId - 1) + downloadedLength;
				int endPos = block * threadId - 1;
				conn.setRequestProperty("Range", "bytes=" + startPos + "-"
						+ endPos);
				conn.setRequestProperty(
						"User-Agent",
						"Mozilla/4.0(compatible; MSIE 8.0; Windows NT 5.2; Trident/4.0; .NET CLR 1.1.4322; .NET CLR 2.0.50727; .NET CLR 3.0.04506.30; .NET CLR 3.0.4506.2152; .NET CLR 3.5.30729)"); // 设置用户代理
				conn.setRequestProperty("Connection", "Keep-Alive"); // 设置Connection的方式
				InputStream is = conn.getInputStream();
				byte[] buffer = new byte[1024];
				int offset = 0;
				Log.i(TAG, "Thread " + this.threadId
						+ " starts to download from position " + startPos);

				RandomAccessFile threadFile = new RandomAccessFile(
						this.saveFile, "rwd");
				threadFile.seek(startPos);

				while (!downloader.getExited()
						&& (offset = is.read(buffer, 0, 1024)) != -1) {
					threadFile.write(buffer, 0, offset);
					downloadedLength += offset;
					downloader.update(this.threadId, downloadedLength);
					downloader.append(offset);
				}
				threadFile.close();
				is.close();

				if (downloader.getExited()) {
					Log.i(TAG, "Thread " + this.threadId + " has been paused");
				} else {
					Log.i(TAG, "Thread " + this.threadId + " download finish");
				}

				this.finished = true;
			} catch (Exception e) {
				this.downloadedLength = -1;
				Log.i(TAG, "Thread " + this.threadId + " : " + e);
			}
		}
	}

	public boolean isFinished() {
		return finished;
	}

	public long getDownloadedLength() {
		return downloadedLength;
	}

}
