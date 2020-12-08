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
                breakPointInfo.tag = cursor.getString(1)
                breakPointInfo.startPoint = cursor.getLong(2)
                breakPointInfo.endPoint = cursor.getLong(3)
                breakPointInfo.blockId = cursor.getLong(4)
                breakPointInfo.eTag = cursor.getString(5)
                resultList.add(breakPointInfo)
            }
            cursor.close()
            return resultList
        }
    }

    //添加断点
    fun addBreakPoint(url: String,tag :String,startPoint:Long,endPoint:Long,blockId:Long,eTag: String) {
        synchronized(this) {
            writableDatabase.execSQL("replace into $TABLE_NAME ( url,tag,start_point,end_point,blockId,eTag ) values ('${url}','${tag}','${startPoint}','${endPoint}','${blockId}','${eTag}')")
        }
    }

    fun deleteBreakPointByUrl(url: String){
        synchronized(this) {
            writableDatabase.execSQL("delete from $TABLE_NAME where url = '$url' ")
        }
    }
    fun deleteBreakPointByTag(tag: String){
        synchronized(this) {
            writableDatabase.execSQL("delete from $TABLE_NAME where tag = '$tag' ")
        }
    }

    fun close() {
        writableDatabase.close()
        databaseHelper.close()
    }
}