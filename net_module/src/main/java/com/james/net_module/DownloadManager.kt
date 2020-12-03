package com.james.net_module

import android.util.Log
import java.io.File
import java.lang.Exception


class DownloadManager {

    private var downloadCallback: DownloadCallback? = null

    /**允许下载的最大个数*/
    private var maxDownloadCount = 5

    /**下载引擎，默认使用okHttp进行下载*/
    private var downLoadEngine: DownLoadEngine? = null
        get() {
            if (field == null) {
                field = OkHttpDownload()
            }
            return field
        }

    private var defaultDownloadCallback: DefaultDownloadCallback? = null
        get() {
            if (field == null) {
                defaultDownloadCallback = DefaultDownloadCallback()
            }
            return field
        }

    private val downloadDispatcher by lazy {
        Dispatcher(maxDownloadCount).apply {
            startDownload = {
                Log.e("AAA","begin start download Index ${it.tag}")
                downLoadEngine?.startDownload(it, defaultDownloadCallback!!)
            }
            cancelDownload = {
                downLoadEngine?.cancel(it)
            }
        }
    }


    /**
     * 设置最大下载个数，默认是5个
     */
    fun setMaxDownloadCount(maxDownloadCount: Int): DownloadManager {
        this.maxDownloadCount = maxDownloadCount
        return this
    }

    /**
     * 设置自定义下载引擎。默认的下载引擎是 OkHttpDownload
     * @see com.tbright.ktbaselibrary.net.download.OkHttpDownload
     */
    fun setDownLoadEngine(downLoadEngine: DownLoadEngine?): DownloadManager {
        this.downLoadEngine = downLoadEngine
        return this
    }

    /**
     * 添加下载任务
     */
    fun addTask(downloadTask: DownloadTask, downloadCallback: DownloadCallback): DownloadManager {
        this.downloadCallback = downloadCallback
        downloadDispatcher.enqueue(downloadTask)
        return this
    }

    /**
     * 添加下载任务
     */
    fun addTask(downloadTasks: List<DownloadTask>, downloadCallback: DownloadCallback): DownloadManager {
        this.downloadCallback = downloadCallback
        downloadDispatcher.enqueue(downloadTasks)
        return this
    }

    fun addTask(url: String, file: File, priority: Int = 0, downloadCallback: DownloadCallback): DownloadManager {
        this.downloadCallback = downloadCallback
        val downloadTask = DownloadTask.Build(url, file).setPriority(priority).build()
        downloadDispatcher.enqueue(downloadTask)
        return this
    }

    fun addTask(url: String, file: File, tag :String,priority: Int = 0, downloadCallback: DownloadCallback): DownloadManager {
        this.downloadCallback = downloadCallback
        val downloadTask = DownloadTask.Build(url, file).setTag(tag).setPriority(priority).build()
        downloadDispatcher.enqueue(downloadTask)
        return this
    }

    fun addTask(url: String, path: String, priority: Int = 0, downloadCallback: DownloadCallback): DownloadManager {
        this.downloadCallback = downloadCallback
        val downloadTask = DownloadTask.Build(url, path).setPriority(priority).build()
        downloadDispatcher.enqueue(downloadTask)
        return this
    }

    fun addTask(url: String, path: String, tag :String, priority: Int = 0, downloadCallback: DownloadCallback): DownloadManager {
        this.downloadCallback = downloadCallback
        val downloadTask = DownloadTask.Build(url, path).setTag(tag).setPriority(priority).build()
        downloadDispatcher.enqueue(downloadTask)
        return this
    }
    /**
     * 取消下载
     */
    fun cancel(downloadTask: DownloadTask) {
        downloadDispatcher.cancel(downloadTask)
    }

    /**
     * 取消下载
     */
    fun cancelByUrl(url: String) {
        downloadDispatcher.cancelByUrl(url)
    }

    /**
     * 取消下载
     */
    fun cancelByTag(tag: String) {
        downloadDispatcher.cancelByTag(tag)
    }

    /**
     * 取消所有下载
     */
    fun cancelAll() {
        downloadDispatcher.cancelAll()
    }

    /**暂停下载*/
    fun pause(){

    }

    /**暂停下载*/
    fun pauseByTag(){

    }

    /**暂停下载*/
    fun pauseByUrl(){

    }

    fun destroy() {
        downloadDispatcher.destroy()
        defaultDownloadCallback = null
    }


    private inner class DefaultDownloadCallback : DownloadCallback {
        override fun started(task: DownloadTask) {
            downloadCallback?.started(task)
        }

        override fun progress(task: DownloadTask, currentOffset: Long, totalLength: Long) {
            downloadCallback?.progress(task, currentOffset, totalLength)
        }

        override fun canceled(task: DownloadTask) {
            downloadCallback?.canceled(task)
        }

        override fun error(task: DownloadTask, e: Exception) {
            downloadCallback?.error(task, e)
            downloadDispatcher.cancel(task)
        }

        override fun completed(task: DownloadTask) {
            downloadCallback?.completed(task)
            downloadDispatcher.cancel(task)
        }
    }
}