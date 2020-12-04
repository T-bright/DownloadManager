package com.james.net_module

import android.util.Log
import com.james.net_module.okhttpdownload.OkHttpDownload
import java.io.File
import java.lang.NullPointerException
import java.util.ArrayDeque
import kotlin.Exception


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
                downLoadEngine?.startDownload(it, defaultDownloadCallback!!)
            }

            cancelDownload = {
                if(it.downloadType == DownloadTask.CANCEL){
                    downLoadEngine?.cancel(it)
                }else{
                    Log.e("CCC","2 pause")
                    downLoadEngine?.pause(it)
                }
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

    fun addTask(url: String, file: File, tag: String, priority: Int = 0, downloadCallback: DownloadCallback): DownloadManager {
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

    fun addTask(url: String, path: String, tag: String, priority: Int = 0, downloadCallback: DownloadCallback): DownloadManager {
        this.downloadCallback = downloadCallback
        val downloadTask = DownloadTask.Build(url, path).setTag(tag).setPriority(priority).build()
        downloadDispatcher.enqueue(downloadTask)
        return this
    }

    /**
     * 取消下载
     */
    private fun cancel(downloadTask: DownloadTask?): DownloadTask? {
        if(downloadTask != null){
            downloadTask?.downloadType = DownloadTask.CANCEL
            return downloadDispatcher.cancel(downloadTask)
        }else{
            //TODO 这里有一个bug，就是先暂停了，是找不到下载任务的，所以点击取消按钮，会没有反应。
            // 这里特俗处理一下。将结果回调出去，同时在暂停任务中找到暂停任务，清理掉相关的数据库信息
            return null
        }
    }

    /**
     * 取消下载
     */
    fun cancelByUrl(url: String): DownloadTask? {
        return cancel(findDownloadTaskByUrl(url))
    }

    /**
     * 取消下载
     */
    fun cancelByTag(tag: String): DownloadTask? {
        return cancel(findDownloadTaskByTag(tag))
    }

    /**
     * 取消所有下载
     */
    fun cancelAll() {
        downloadDispatcher.cancelAll()
    }

    //暂停下载时的缓存
    private val pauseDownloadCaches = ArrayDeque<DownloadTask>()

    /**暂停下载*/
    fun pauseByTag(tag: String) {
        pause(findDownloadTaskByTag(tag))
    }

    /**暂停下载*/
    fun pauseByUrl(url: String) {
        pause(findDownloadTaskByUrl(url))
    }

    /**暂停下载*/
    private fun pause(pauseDownloadTask: DownloadTask?) {
        //不是下载状态，就不执行暂停操作了
        if(pauseDownloadTask?.isDownLoading() == false) return
        Log.e("CCC","1 pause")
        //如果支持断点下载，就可以执行暂停操作。如果不支持断点下载，给一个异常
        if(pauseDownloadTask?.isSupportBreakpointDownloads == true){
            pauseDownloadTask.downloadType = DownloadTask.PAUSE
            pauseDownloadCaches.add(pauseDownloadTask)
            downloadDispatcher.cancel(pauseDownloadTask)
        }else{
            pauseDownloadTask?.let { downloadCallback?.onPause(it, NullPointerException()) }
        }
    }


    /**开始下载*/
    fun resumeByTag(tag: String) {
        val iterator = pauseDownloadCaches.iterator()
        while (iterator.hasNext()) {
            val downloadTask = iterator.next()
            if (downloadTask.tag == tag) {
                iterator.remove()
                downloadDispatcher.enqueue(downloadTask)
                break
            }
        }
    }

    /**开始下载*/
    fun resumeByUrl(url: String) {
        val iterator = pauseDownloadCaches.iterator()
        while (iterator.hasNext()) {
            val downloadTask = iterator.next()
            if (downloadTask.url == url) {
                iterator.remove()
                downloadDispatcher.enqueue(downloadTask)
                break
            }
        }
    }

    /**
     * 通过url查找下载任务，只能查找到 正在下载 和 等待下载 中的任务。
     * 不包括已暂停和已取消的下载任务。
     *
     * 详细请看：[Dispatcher.findDownloadTaskByUrl]
     */
    fun findDownloadTaskByUrl(url: String): DownloadTask? {
        return downloadDispatcher.findDownloadTaskByUrl(url)
    }

    /**
     * 通过tag查找下载任务，只能查找到 正在下载 和 等待下载中的任务。
     * 不包括已暂停和已取消的下载任务。
     *
     * 详细请看：[Dispatcher.findDownloadTaskByTag]
     */
    fun findDownloadTaskByTag(tag: String): DownloadTask? {
        return downloadDispatcher.findDownloadTaskByTag(tag)
    }


    fun destroy() {
        downloadDispatcher.destroy()
        pauseDownloadCaches.clear()
        defaultDownloadCallback = null
        downLoadEngine?.cancelAll()
    }


    private inner class DefaultDownloadCallback : DownloadCallback {
        override fun onWait(task: DownloadTask) {
            downloadCallback?.onWait(task)
        }

        override fun onStart(task: DownloadTask) {
            downloadCallback?.onStart(task)
        }

        override fun onProgress(task: DownloadTask, currentOffset: Long, totalLength: Long) {
            downloadCallback?.onProgress(task, currentOffset, totalLength)
        }

        override fun onPause(task: DownloadTask, e: Exception?) {
            downloadCallback?.onPause(task, e)
        }

        override fun onCancel(task: DownloadTask) {
            downloadCallback?.onCancel(task)
        }

        override fun onError(task: DownloadTask, e: Exception?) {
            downloadCallback?.onError(task, e)
            downloadDispatcher.cancel(task)
        }

        override fun onComplete(task: DownloadTask) {
            downloadCallback?.onComplete(task)
            downloadDispatcher.cancel(task)
        }
    }
}