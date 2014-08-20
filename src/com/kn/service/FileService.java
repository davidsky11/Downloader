package com.kn.service;

import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class FileService {

	private DBOpenHelper openHelper;

	public FileService(Context context) {
		openHelper = new DBOpenHelper(context);
	}

	/**
	 * 获取特定URI的每条线程已经下载的文件长度
	 * 
	 * @param path
	 * @return
	 */
	public Map<Integer, Integer> getData(String path) {
		SQLiteDatabase db = openHelper.getReadableDatabase();
		Cursor cursor = db
				.rawQuery(
						"select threadid,  downlength from filedownlog where downpath=?",
						new String[] { path }); // 根据下载路径查询所有线程下载数据，返回的Cursor指向第一条记录之前
		Map<Integer, Integer> data = new HashMap<Integer, Integer>(); // 建立一个哈希表用于存放每条线程的已经下载的文件长度

		while (cursor.moveToNext()) { // 从第一条记录开始遍历Cursor对象
			data.put(cursor.getInt(0), cursor.getInt(1)); // 把线程id和该线程已下载的长度设置进data哈希表中
			data.put(cursor.getInt(cursor.getColumnIndexOrThrow("threadid")),
					cursor.getInt(cursor.getColumnIndexOrThrow("downlength")));
		}
		cursor.close();
		db.close();
		return data; // 返回获得的每条线程和每条线程的下载长度
	}

	/**
	 * 保存每条线程已经下载的文件长度
	 * 
	 * @param path
	 *            下载的路径
	 * @param map
	 *            现在的id和已经下载的长度的集合
	 */
	public void save(String path, Map<Integer, Integer> map) {
		SQLiteDatabase db = openHelper.getWritableDatabase();
		db.beginTransaction(); // 开始事务，因为此处要插入多批数据

		try {
			for (Map.Entry<Integer, Integer> entry : map.entrySet()) { // 采用For-Each的方式遍历数据集合
				db.execSQL(
						"insert into filedownlog(downpath, threadid, downlength) values(?, ?, ?)",
						new Object[] { path, entry.getKey(), entry.getValue() });
				// 插入 特定下载路径	特定线程ID	已经下载的数据
			}
			db.setTransactionSuccessful();		// 设置事务执行的标志为成功
		} finally {
			db.endTransaction();		// 结束一个事务，如果事务设立了成功标记，则提交事务，否则回滚事务
		}
		db.close();
	}

	/**
	 * 实时更新每条线程已经下载的文件长度
	 * @param path
	 * @param threadid
	 * @param pos
	 */
	public void update(String path, int threadid, int pos) {
		SQLiteDatabase db = openHelper.getWritableDatabase();
		db.execSQL(
				"update filedownlog set downlength=? where downpath=? and threadid=?",
				new Object[] { pos, path, threadid });
		// 更新特定下载路径下特定线程已经下载的文件长度
		db.close();
	}

	/**
	 * 当文件下载完成后，删除对应的下载记录
	 * @param path
	 */
	public void delete(String path) {
		SQLiteDatabase db = openHelper.getWritableDatabase();
		db.execSQL("delete from filedownlog where downpath=?",
				new Object[] { path });		// 删除特定下载路径的所有线程记录
		db.close();
	}
}
