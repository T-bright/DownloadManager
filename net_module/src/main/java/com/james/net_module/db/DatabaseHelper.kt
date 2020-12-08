package com.james.net_module.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

const val DB_NAME = "net_module_download.db"
const val TABLE_NAME = "download_break_point"
class DatabaseHelper constructor(
    context: Context,
    name: String = DB_NAME,
    factory: SQLiteDatabase.CursorFactory? = null,
    version: Int = 1
) : SQLiteOpenHelper(context, name, factory, version) {

    override fun onCreate(db: SQLiteDatabase?) {
        val sql = "create table $TABLE_NAME (" +
                "url TEXT NOT NULL ," + //下载的链接地址。这里的url用md5处理了下
                "tag TEXT NOT NULL ," + //下载任务的标识。如果用户不指定，这个默认是md5处理的url。
                "start_point LONG NOT NULL," + //开始下载断点的位置
                "end_point LONG NOT NULL," + //结束下载断点的位置
                "blockId LONG NOT NULL," + //多线程下载时候，分块的id
                "eTag TEXT , " +//这个是断点重新下载时，判断服务器端的资源有没有变化
                "PRIMARY KEY (url, blockId))"
        db?.execSQL(sql)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {

    }
}