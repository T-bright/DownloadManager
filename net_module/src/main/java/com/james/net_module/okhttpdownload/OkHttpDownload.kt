package com.james.net_module.okhttpdownload

import android.text.TextUtils
import com.james.net_module.DownLoadEngine
import com.james.net_module.DownloadCallback
import com.james.net_module.DownloadTask
import com.james.net_module.db.DatabaseManager
import com.james.net_module.utils.Utils
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.*
import java.nio.channels.FileChannel
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
                downloadTasks[call.request().url().toString()]?.let {
                    it.downloadType = DownloadTask.ERROR
                    finish(it, e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                //1、判断满不满足断点下载，如果满足，将下载信息保存在数据库中，如果不满足下载信息则不保存在数据库中
                if (response.isSuccessful) {
                    val url = call.request().url().toString()
                    val nowTask = downloadTasks[url]!!
                    var totalLength = response.body()!!.contentLength() //这个是断点之后的文件长度，不是整个文件的大小
                    var inputStream = response.body()!!.byteStream()
                    //验证是否支持断点下载。如果支持，要将相应的下载进度保存在数据库里，以便暂停之后继续下载。
                    //如果不支持断点下载，就从头开始下载
                    var isSupportBreakpointDownloads = response.code() == 206 || (response.headers().get("Accept-Ranges") != null && response.headers().get("Accept-Ranges") != "none") || response.headers().get("Content-Range") != null
                    var eTag = "" //支持断点下载时，验证服务器的文件是否被修改。这个保存在数据库里，下一次请求时会放在 If-Range 请求头中
                    var process: Long = 0
                    if (isSupportBreakpointDownloads) {
                        eTag = response.headers().get("ETag").orEmpty()
                        if (eTag.isEmpty()) {
                            eTag = response.headers().get("Last-Modified").orEmpty()
                        }
                        var contentRange = response.headers().get("Content-Range")
                        var contentRanges = contentRange?.split(" ")
                        if (contentRanges?.size ?: 0 > 1) {
                            process = contentRanges?.get(1)?.split("/")?.get(0)?.split("-")?.get(0)?.toLong() ?: 0
                        }
                    }
                    nowTask.isSupportBreakpointDownloads = isSupportBreakpointDownloads
                    parseStream(inputStream, isSupportBreakpointDownloads, nowTask, process, totalLength, eTag)
                }
            }
        })
    }

    /**
     * @param inputStream：下载的文件流
     * @param isSupportBreakpointDownloads：是否支持断点下载
     * @param downloadTask：下载的任务
     * @param currentLength：断点下载时，开始的下载点
     * @param totalLength：断点之后文件总大小。如果是从文件开始的地方开始下载，这个长度就是整个文件的大小
     * @param eTag：用来判断服务器的文件是否发生了变化
     */
    private fun parseStream(inputStream: InputStream, isSupportBreakpointDownloads: Boolean, downloadTask: DownloadTask, currentLength: Long, totalLength: Long, eTag: String) {
        val buffer = ByteArray(downloadTask.bufferSize)
        var process = currentLength
        var allFileLength = totalLength + currentLength
        downloadTask.totalLength = allFileLength
        downloadTask.process = currentLength

        downloadTask.downloadType = DownloadTask.START
        downloadCallback?.onStart(downloadTask)

        var randomAccessFile = RandomAccessFile(File(downloadTask.filePath), "rwd")
        var channelOut = randomAccessFile.channel
        var mappedBuffer = channelOut.map(FileChannel.MapMode.READ_WRITE, process, allFileLength)
        var urlMd5 = Utils.md5(downloadTask.url)

        //tag下载任务的标识。如果用户不指定，这个默认是md5处理的url。
        //这个保存在数据库里，目的是用来通过tag处理取消下载时，删除本地数据库的信息。默认将他设置成md5处理的url，防止被误删。
        val tag = if (TextUtils.isEmpty(downloadTask.tag)) urlMd5 else downloadTask.tag
        var exception : Exception? = null
        try {
            var len = inputStream.read(buffer)
            while (len != -1) {
                if (downloadTask.isDownLoading()) {
                    process += len
                    if (isSupportBreakpointDownloads) {
                        DatabaseManager.addBreakPoint(urlMd5, tag, process, allFileLength, 1, eTag)
                    }
                    downloadTask.process = process
                    downloadCallback?.onProgress(downloadTask, process, allFileLength)
                    mappedBuffer.put(buffer, 0, len)
                    len = inputStream.read(buffer)
                } else {
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (downloadTask.downloadType != DownloadTask.PAUSE && downloadTask.downloadType != DownloadTask.CANCEL) {
                downloadTask.downloadType = DownloadTask.ERROR
                exception = e
            }
        } finally {
            channelOut.close()
            randomAccessFile.close()
            inputStream.close()
            if (process == allFileLength) {
                downloadTask.downloadType = DownloadTask.COMPLETE
            }

            //下载完成或者取消下载。要删除数据库中的信息
            if(downloadTask.downloadType == DownloadTask.COMPLETE || downloadTask.downloadType == DownloadTask.CANCEL){
                DatabaseManager.deleteBreakPointByUrl(urlMd5)
            }

            finish(downloadTask,exception)
        }
    }

    override fun cancel(downloadTask: DownloadTask) {
        downloadTask.downloadType = DownloadTask.CANCEL
        finish(downloadTask)
    }

    override fun pause(downloadTask: DownloadTask) {
        downloadTask.downloadType = DownloadTask.PAUSE
        finish(downloadTask)
    }

    override fun cancelAll() {
        downloadTasks.values.forEach {
            cancel(it)
        }
    }

    override fun destroy() {
        callCaches.values.forEach {
            it.cancel()
        }
    }

    private fun finish(downloadTask: DownloadTask, e: Exception? = null) {
        val url = downloadTask.url
        callCaches[url]?.cancel()
        callCaches.remove(url)
        downloadTasks.remove(url)
        when (downloadTask.downloadType) {
            DownloadTask.PAUSE -> downloadCallback?.onPause(downloadTask)
            DownloadTask.CANCEL -> downloadCallback?.onCancel(downloadTask)
            DownloadTask.ERROR -> downloadCallback?.onError(downloadTask, e)
            DownloadTask.COMPLETE -> downloadCallback?.onComplete(downloadTask)
        }
    }
}