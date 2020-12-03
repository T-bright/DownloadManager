package com.james.net_module

import com.james.net_module.db.DatabaseManager
import com.james.net_module.interceptor.BreakPointInterceptor
import com.james.net_module.utils.Utils
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class OkHttpDownload : DownLoadEngine {
    private var okHttpClient: OkHttpClient
    private val CONNECT_TIMEOUT: Long = 60 //超时时间，秒
    private val READ_TIMEOUT: Long = 60 //读取时间，秒
    private val WRITE_TIMEOUT: Long = 60 //写入时间，秒

    init {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = HttpLoggingInterceptor.Level.HEADERS

        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(BreakPointInterceptor())
            .addInterceptor(loggingInterceptor)
        okHttpClient = builder.build()
    }

    /**okhttp的call缓存。键是下载的url*/
    private val callCaches = hashMapOf<String, Call>()

    /**下载任务的缓存，方便做取消任务*/
    private val downloadTasks = hashMapOf<String, DownloadTask>()

    /**
     * 这个downloadCallback是[DownloadManager.DefaultDownloadCallback]的实例对象，并不是外部调用者传进来的回调对象。
     * 这样做，是为了下载模块，更加专注于下载逻辑。
     */
    private var downloadCallback: DownloadCallback? = null

    override fun startDownload(downloadTask: DownloadTask, callback: DownloadCallback) {
        this.downloadCallback = callback
        downloadTask.downloadType = DownloadTask.START
        downloadCallback?.started(downloadTask)

        //添加相关的Header信息
        val headersBuilder = Headers.Builder()
        if (downloadTask.headerMaps.isNotEmpty()) {
            val iterator = downloadTask.headerMaps.iterator()
            while (iterator.hasNext()) {
                headersBuilder.add(iterator.next().key, iterator.next().value)
            }
        }

        val request: Request = Request.Builder()
            .url(downloadTask.url)
            .headers(headersBuilder.build())
            .build()
        val call = okHttpClient.newCall(request)
        val url = downloadTask.url

        callCaches[url] = call
        downloadTasks[url] = downloadTask

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val nowTask = downloadTasks[call.request().url().toString()]!!
                nowTask.downloadType = DownloadTask.ERROR
                finish(nowTask, e)
            }

            override fun onResponse(call: Call, response: Response) {
                //1、判断满不满足断点下载，如果满足，将下载信息保存在数据库中，如果不满足下载信息则不保存在数据库中
                if (response.isSuccessful) {
                    val url = call.request().url().toString()
                    val nowTask = downloadTasks[url]!!
                    nowTask.totalLength = response.body()!!.contentLength()
                    var inputStream = response.body()!!.byteStream()
                    var isSupportBreakpointDownloads = response.code() == 206 || (response.headers().get("Accept-Ranges") != null && response.headers().get("Accept-Ranges") != "none") || response.headers().get("Content-Range") != null
                    var eTag = ""
                    if(isSupportBreakpointDownloads){
                        eTag = response.headers().get("ETag").orEmpty()
                        if(eTag.isEmpty()){
                            eTag = response.headers().get("Last-Modified").orEmpty()
                        }
                    }
                    parseStream(inputStream,isSupportBreakpointDownloads,nowTask,eTag)
                }
            }
        })
    }

    private fun parseStream(inputStream: InputStream, isSupportBreakpointDownloads: Boolean,downloadTask: DownloadTask,eTag :String) {
        val buffer = ByteArray(downloadTask.bufferSize)
        var len: Int = 0
        var process: Long = 0
        var fos = FileOutputStream(File(downloadTask.filePath))
        var urlMd5  = Utils.md5(downloadTask.url)
        var length = downloadTask.totalLength
        while (((inputStream.read(buffer)).also { len = it }) != -1) {
            fos.write(buffer, 0, len)
            process += len
            if (downloadTask.isDownLoading()) {
                downloadTask.process = process
                if(isSupportBreakpointDownloads){
                    DatabaseManager.addBreakPoint(urlMd5,process,length,1,eTag)
                }
                downloadCallback?.progress(downloadTask, process, downloadTask.totalLength)
            } else {
                break
            }
        }
        fos.close()
        inputStream.close()
        if (process == downloadTask.totalLength) {
            downloadTask.downloadType = DownloadTask.FINISH
            finish(downloadTask)
        }
    }

    override fun cancel(downloadTask: DownloadTask) {
        downloadTask.downloadType = DownloadTask.CANCEL
        finish(downloadTask)
    }

    override fun cancelAll() {
        callCaches.values.forEach {
            it.cancel()
        }
        callCaches.clear()
    }

    fun destroy() {
        cancelAll()
    }

    private fun finish(downloadTask: DownloadTask, e: Exception? = null) {
        val url = downloadTask.url
        callCaches[url]?.cancel()
        callCaches.remove(url)
        downloadTasks.remove(url)
        when (downloadTask.downloadType) {
            DownloadTask.CANCEL -> downloadCallback?.canceled(downloadTask)
            DownloadTask.ERROR -> downloadCallback?.error(downloadTask, e!!)
            DownloadTask.FINISH -> downloadCallback?.completed(downloadTask)
        }
    }
}