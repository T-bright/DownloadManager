package com.james.downloadmanager

import android.Manifest
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import com.james.net_module.DownloadCallback
import com.james.net_module.DownloadManager
import com.james.net_module.DownloadTask
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File
import kotlin.Exception

class MainActivity : AppCompatActivity() {
    private val downloadFileUrl1 =
        "https://fga1.market.xiaomi.com/download/AppStore/085194fcd6b498a6efcda0b373682500d96418744/com.tencent.mobileqq.apk"

    private val downloadFileUrl2 =
        "https://fga1.market.xiaomi.com/download/AppStore/052c14f6e6c927ab99d18a9a515ba4fcacb40b9a3/com.tencent.gamereva.apk"

    private val downloadFileUrl3 =
        "https://fga1.market.xiaomi.com/download/AppStore/03c0724b058f743d91103bd924a3e69a94b6d3fcd/com.lhh.asia.btgame.apk"

    private val downloadFileUrl4 =
        "https://fga1.market.xiaomi.com/download/AppStore/0b1924faf32e7ead14c7ad8ade55e322aa440afa0/com.tencent.gamehelper.speed.apk"

    private val downloadFileUrl5 = "http://61.191.199.181:22003/upload/file/8c0/10/8/b44c6eb01c01000/5a2637580e0.mp3"

    private val dataLists =
        arrayListOf(DownloadBean(downloadFileUrl5)
            , DownloadBean(downloadFileUrl2)
            , DownloadBean(downloadFileUrl3)
            , DownloadBean(downloadFileUrl4)
        )


    private val myAdapter by lazy {
        DownloadAdapter(dataLists).apply {
            setOnItemChildClickListener { adapter, view, position ->
                when(view.id){
                    R.id.tvDownload->{//暂停或者开始下载
                        var data = dataLists[position]
                        if(data.isDownloadIng){
                            downloadManager.pauseByTag("$position")
                        }
                        else if(data.downloadType == DownloadTask.PAUSE){
                            downloadManager.resumeByTag("$position")
                        }
                        else{
                            download(position,data)
                        }
                    }
                    R.id.tvCancel->{//取消下载
                        downloadManager.cancelByTag("$position")
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE),2)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = myAdapter

        allStart.setOnClickListener {
            allDownload()
        }
        allEnd.setOnClickListener {
            downloadManager.cancelAll()
        }
    }

    var downloadManager = DownloadManager()

    private var type = 0
    private var mainScope = MainScope()

    private fun allDownload() {
        for (index in 0 until dataLists.size) {
            var downloadBean = dataLists[index]
            download(index,downloadBean)
        }
    }

    fun download(index :Int,downloadBean :DownloadBean){
        var fileName = downloadBean.url.substringAfterLast('/', "")
        myAdapter.setProgress(index,0.00f,DownloadTask.START)
        downloadManager.addTask(downloadBean.url, File(cacheDir.absolutePath,fileName),"$index",0,object :DownloadCallback{
            override fun onWait(task: DownloadTask) {

            }

            override fun onStart(task: DownloadTask) {
                mainScope.launch(Dispatchers.Main) {
                    Log.e("AAA","started :${task.tag.toInt()}")
                    myAdapter.setProgress(task.tag.toInt(),null,DownloadTask.START)
                }
            }

            override fun onProgress(task: DownloadTask, currentOffset: Long, totalLength: Long) {
                mainScope.launch(Dispatchers.Main) {
                    var progress = (task.process * 1.0f / task.totalLength * 100)
                    Log.e("AAA","onProgress :${task.tag.toInt()} ：progress ：${progress}")
                    myAdapter.setProgress(task.tag.toInt(),progress,DownloadTask.START)
                }

            }

            override fun onPause(task: DownloadTask, e: Exception?) {
                mainScope.launch(Dispatchers.Main) {
                    myAdapter.setProgress(task.tag.toInt(),null,DownloadTask.PAUSE)
                }
            }

            override fun onCancel(task: DownloadTask) {
                Log.e("AAA","canceled :${task.tag.toInt()}")
                mainScope.launch(Dispatchers.Main) {
                    myAdapter.setProgress(task.tag.toInt(),0f,DownloadTask.CANCEL)
                }
            }

            override fun onError(task: DownloadTask, e: Exception?) {
                Log.e("AAA","error :${task.tag.toInt()}")
                mainScope.launch(Dispatchers.Main) {
                    var progress = (task.process * 1.0f / task.totalLength * 100)
                    myAdapter.setProgress(task.tag.toInt(),progress,DownloadTask.ERROR)
                }
            }

            override fun onComplete(task: DownloadTask) {
                Log.e("AAA","completed :${task.tag.toInt()}")
                mainScope.launch(Dispatchers.Main) {
                    myAdapter.setProgress(task.tag.toInt(),100f,DownloadTask.COMPLETE)
                }
            }
        })
    }
}