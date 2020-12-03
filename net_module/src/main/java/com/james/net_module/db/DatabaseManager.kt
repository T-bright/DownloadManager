package com.james.net_module.db

import android.annotation.SuppressLint
import android.database.sqlite.SQLiteDatabase
import com.james.net_module.BreakPointInfo
import com.james.net_module.utils.NetModuleProvider

object DatabaseManager {
    private var databaseHelper: DatabaseHelper = DatabaseHelper(NetModuleProvider.mContext!!)
    private var writableDatabase: SQLiteDatabase

    init {
        writableDatabase = databaseHelper.writableDatabase
    }

    //查询断点信息
    @SuppressLint("Recycle")
    fun findBreakPoint(url: String): MutableList<BreakPointInfo> {
        synchronized(this) {
            val resultList = arrayListOf<BreakPointInfo>()
            val cursor = writableDatabase.rawQuery("select * from $TABLE_NAME where url= ?", arrayOf(url))
            while (cursor?.moveToNext() == true) {
                val breakPointInfo = BreakPointInfo()
                breakPointInfo.url = cursor.getString(0)
                breakPointInfo.startPoint = cursor.getLong(1)
                breakPointInfo.endPoint = cursor.getLong(2)
                breakPointInfo.blockId = cursor.getLong(3)
                breakPointInfo.eTag = cursor.getString(4)
                resultList.add(breakPointInfo)
            }
            cursor.close()
            return resultList
        }
    }

    //添加断点
    fun addBreakPoint(url: String,startPoint:Long,endPoint:Long,blockId:Long,eTag: String) {
        synchronized(this) {
            writableDatabase.execSQL("replace into $TABLE_NAME ( url,start_point,end_point,blockId,eTag ) values ('${url}','${startPoint}','${endPoint}','${blockId}','${eTag}')")
        }
    }

    fun close() {
        writableDatabase.close()
        databaseHelper.close()
    }
}