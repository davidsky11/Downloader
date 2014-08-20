package com.kn.service;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.util.Log;

public class FileDownloader {

	private static final String TAG = "FileDownloader";
	private static final int RESPONSEOK = 200; // 响应码200，即访问成功
	private Context context;
	private FileService fileService;		// 本地数据库业务
	private boolean exited;					// 停止下载标志
	private int downloadedSize = 0;			// 已下载文件长度
	private int fileSize = 0;				// 原始文件长度
	private DownloadThread[] threads;		// 根据线程数设置下载线程池
	private File saveFile;					// 数据保存的本地文件（路径）
	private Map<Integer, Integer> data = new ConcurrentHashMap<Integer, Integer>();		// 缓存各线程下载的长度
	private int block;						// 每条线程下载的长度
	private String downloadUrl;				// 下载路径

	/**
	 * 获取线程数
	 * @return
	 */
	public int getThreadSize() {
		return threads.length;
	}

	/**
	 * 退出下载
	 */
	public void exit() {
		this.exited = true;		// 设置退出标志为true
	}

	public boolean getExited() {
		return this.exited;
	}

	/**
	 * 获取文件大小
	 * @return
	 */
	public int getFileSize() {
		return fileSize;
	}

	/**
	 * 累计已下载大小
	 * PS: 使用同步关键字解决并发访问
	 * @param size
	 */
	protected synchronized void append(int size) {
		downloadedSize += size; // 把实时下载的长度加入到总下载长度中
	}

	/**
	 * 更新指定线程最后下载的位置
	 * @param threadid	线程id
	 * @param pos		最后下载的位置
	 */
	protected synchronized void update(int threadid, int pos) {
		this.data.put(threadid, pos); 			// 把指定线程id的线程赋予最新的下载长度，以前的值会被覆盖
		this.fileService.update(this.downloadUrl, threadid, pos); // 更新数据库中指定线程的下载长度
	}

	/**
	 * 构建文件下载器
	 * 
	 * @param context
	 * @param downloadUrl
	 *            下载路径
	 * @param fileSaveDir
	 *            文件保存目录
	 * @param threadNum
	 *            下载线程数
	 */
	public FileDownloader(Context context, String downloadUrl,
			File fileSaveDir, int threadNum) {
		try {
			this.context = context;
			this.downloadUrl = downloadUrl;
			fileService = new FileService(this.context);
			URL url = new URL(this.downloadUrl);
			if (!fileSaveDir.exists())			// 如果指定的文件不存在，则创建目录，此处可以创建多层目录
				fileSaveDir.mkdirs();
			this.threads = new DownloadThread[threadNum];

			HttpURLConnection conn = (HttpURLConnection) url.openConnection();		// 建立一个远程连接句柄，此时尚未真正连接
			conn.setConnectTimeout(5 * 1000);		// 超时时间：5秒
			conn.setRequestMethod("GET");			// 请求方式：GET
			conn.setRequestProperty(
					"Accept",
					"image/gif, image/jpeg, image/pjpeg, application/x-shockwave-flash, application/xaml+xml, application/vnd.ms-xpsdocument, application/x-ms-xbap, application/x-ms-application, application/vnd.ms-excel, application/vnd.ms-powerpoint, application/msword, */*");
			// 设置客户端可以接收的媒体类型
			conn.setRequestProperty("Accept-Language", "zh-CN");	// 设置客户端
			conn.setRequestProperty("Referer", downloadUrl);		// 设置请求的来源页面，便于服务端进行来源统计
			conn.setRequestProperty("Charset", "UTF-8");			// 设置客户端编码
			conn.setRequestProperty(
					"User-Agent",
					"Mozilla/4.0(compatible; MSIE 8.0; Windows NT 5.2; Trident/4.0; .NET CLR 1.1.4322; .NET CLR 2.0.50727; .NET CLR 3.0.04506.30; .NET CLR 3.0.4506.2152; .NET CLR 3.5.30729)"); // 设置用户代理
			conn.setRequestProperty("Connection", "Keep-Alive"); // 设置Connection的方式
			conn.connect();				// 和远程资源建立真正的连接，但尚无返回的数据流
			printResponseHeader(conn); // 打印返回的HTTP头字段集合
			if (conn.getResponseCode() == RESPONSEOK) {		// 获取返回的状态码 200 ?
				this.fileSize = conn.getContentLength();	// 根据响应获取文件大小
				
				if (this.fileSize <= 0)
					throw new RuntimeException("Unkown file size");
				
				String filename = getFileName(conn);				// 获取文件名称
				this.saveFile = new File(fileSaveDir, filename);	// 根据文件保存目录和文件名构建保存文件
				Map<Integer, Integer> logdata = fileService
						.getData(downloadUrl);						// 获取下载记录

				if (logdata.size() > 0) {							// 如果下载记录存在
					for (Map.Entry<Integer, Integer> entry : logdata.entrySet()) {
						data.put(entry.getKey(), entry.getValue()); // 把各线程已经下载的数据长度放入data中
					}
				}

				if (this.data.size() == this.threads.length) {		// 如果已经下载的数据的线程数和现在设置的线程数相同，则计算所有线程已经下载的数据总长度
					for (int i = 0; i < this.threads.length; i++) {
						this.downloadedSize += this.data.get(i + 1);	// 计算已经下载的数据之和
					}
					Log.i(TAG, "已经下载的长度" + this.downloadedSize + "个字节");
				}

				this.block = (this.fileSize % this.threads.length) == 0 ? this.fileSize
						/ this.threads.length
						: this.fileSize / this.threads.length + 1;		// 计算每条线程下载的数据长度
			} else {
				Log.i(TAG, "服务器相应错误：" + conn.getResponseCode() + " >>> "
						+ conn.getResponseMessage());
				throw new RuntimeException("Server response error");
			}
		} catch (Exception e) {
			Log.e(TAG, e.toString());
			throw new RuntimeException("Can't connection this url");
		}
	}

	/**
	 * 获取文件名
	 * @param conn
	 * @return
	 */
	private String getFileName(HttpURLConnection conn) {
		String filename = this.downloadUrl.substring(this.downloadUrl.lastIndexOf('/') + 1);	// 从下载路径的字符串中获取文件名称
		if (filename == null || "".equals(filename.trim())) {		// 如果没有获取到文件名称
			for (int i = 0; ; i++) {
				String mine = conn.getHeaderField(i);	// 从返回的流中获取特定索引的头字段值
				if (mine == null)						// 如果遍历到了返回头末尾，则退出循环
					break;
				if ("content-disposition".equals(conn.getHeaderFieldKey(i).toLowerCase())) {		// 获取content-disposition返回头字段，里面可能会包含文件名
					Matcher m = Pattern.compile(".*filename=(.*)").matcher(mine.toLowerCase());		// 使用正则表达式查询文件名
					if (m.find()) 
						return m.group(1);		// 如果有符合正则表达式规则的字符串
				}
			}
			filename = UUID.randomUUID() + ".tmp";		// 有网卡上标识数字以及CPU时钟的唯一数字生成的一个16字节的二进制作为文件名
		}
		return filename;
	}
	
	/**
	 * 开始下载文件
	 * @param listener		监听下载数量的变化，如果不需要了解实时下载的数量，可以设置为null
	 * @return				已下载文件大小
	 * @throws Exception
	 */
	public int download(DownloadProgressListener listener) throws Exception {
		try {
			RandomAccessFile randOut = new RandomAccessFile(this.saveFile, "rwd");
			if (this.fileSize > 0)
				randOut.setLength(this.fileSize);		// 设置文件的大小
			randOut.close();
			
			URL url = new URL(this.downloadUrl);
			if (this.data.size() != this.threads.length) {		// 如果原先未曾下载或原先的下载线程数与现在的线程数不一致
				this.data.clear();
				for (int i = 0; i < this.threads.length; i++) {	// 遍历线程池
					this.data.put(i+1, 0);		// 初始化每条线程已经下载的数据长度为0
				}
				this.downloadedSize = 0;		// 设置已经下载的长度为0
			} 
			
			for (int i = 0; i < this.threads.length; i++) {
				int downloadedLength = this.data.get(i+1);		// 通过特定的线程id获取该线程已经下载的数据长度
				if (downloadedLength < this.block && this.downloadedSize < this.fileSize) {		// 判断线程是否已经完成下载，否则继续下载
					this.threads[i] = new DownloadThread(this, url, this.saveFile, this.block, this.data.get(i+1), i+1);	// 初始化特定id的线程
					this.threads[i].setPriority(7);		// 设置线程的优先级，Thread.NORM_PRIORITY = 5
					this.threads[i].start();			// 启动线程
				} else {
					this.threads[i] = null;				// 表明该线程已经完成下载任务
				}
			}
			fileService.delete(this.downloadUrl);	// 如果存在下载记录，删除它们，然后重新添加
			fileService.save(this.downloadUrl, this.data);	// 把已经下载的实时数据写入数据库
			boolean notFinished = true;		// 下载未完成
			
			while (notFinished) {			// 循环判断所有线程是否完成下载
				Thread.sleep(900);
				notFinished = false;
				for (int i = 0; i < this.threads.length; i++) {
					if (this.threads[i] != null && !this.threads[i].isFinished()) {
						notFinished = true;
						if (this.threads[i].getDownloadedLength() == -1) {
							this.threads[i] = new DownloadThread(this, url, this.saveFile, this.block, this.data.get(i+1), i+1);
							this.threads[i].setPriority(7);
							this.threads[i].start();
						}
					}
				}
				if (listener != null)
					listener.onDownloadSize(this.downloadedSize);	// 通知目前已经下载完成的数据长度
			}
			if (downloadedSize == this.fileSize)
				fileService.delete(this.downloadUrl);	// 下载完成删除记录
		} catch (Exception e) {
			Log.e(TAG, e.toString());
			throw new Exception("File downloads error");
		}
		return this.downloadedSize;
	}
	
	public static Map<String, String> getHttpResponseHeader(HttpURLConnection http) {
		Map<String, String> header = new LinkedHashMap<String, String>();
		for (int i = 0; ; i++) {
			String fieldValue = http.getHeaderField(i);
			if (fieldValue == null) 
				break;
			header.put(http.getHeaderFieldKey(i), fieldValue);
		}
		return header;
	}
	
	public static void printResponseHeader(HttpURLConnection conn) {
		Map<String, String> header = getHttpResponseHeader(conn);
		for (Map.Entry<String, String> entry : header.entrySet()) {
			String key = entry.getKey() != null ? entry.getKey() + ":" : "";
			Log.i(TAG, key + entry.getValue());
		}
	}
	
}
