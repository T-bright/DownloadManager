package com.james.net_module

import android.util.Log
import com.james.net_module.db.DatabaseManager
import com.james.net_module.okhttpdownload.OkHttpDownload
import com.james.net_module.utils.Utils
import java.io.File
import java.lang.NullPointerException
import java.util.concurrent.PriorityBlockingQueue
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
            readyDownload = {
                downloadCallback?.onWait(it)
            }

            startDownload = {
                downLoadEngine?.startDownload(it, defaultDownloadCallback!!)
            }

            cancelDownload = {
                if (it.downloadType == DownloadTask.CANCEL) {
                    downLoadEngine?.cancel(it)
                } else {
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
     * 设置自定义下载引擎。默认的下载引擎是 [OkHttpDownload]
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
        downloadTask?.downloadType = DownloadTask.CANCEL
        return downloadDispatcher.cancel(downloadTask)
    }

    /**
     * 通过下载的url来取消下载
     * @param url:下载的url
     */
    fun cancelByUrl(url: String): DownloadTask? {
        var downloadTask = findDownloadTaskByUrl(url)
        if (downloadTask == null) {//如果为空，说明当前任务已经被暂停了。此时要到暂停的任务队列中去找。同时还要清除掉相关的数据库信息
            downloadTask = findPauseDownload(null, url)
            if(downloadTask == null){
                this.downloadCallback?.onError(DownloadTask.Build(url).build(),NullPointerException("未找到当前url链接的下载任务：${url}"))
                return null
            }
            //下载任务在暂停队列中，说明本地数据库是有保存下载的信息的，此时需要清除数据库中的信息。
            DatabaseManager.deleteBreakPointByUrl(Utils.md5(url))
            downloadTask.downloadType = DownloadTask.CANCEL
            this.downloadCallback?.onCancel(downloadTask)
            return downloadTask
        } else {//不为空，说明下载任务还在 Dispatcher 的两个下载任务队列中
            return cancel(downloadTask)
        }
    }

    /**
     * 如果之前设置了tag，可以通过tag取消下载。如果之前没有设置tag，则没法取消下载。这个需要注意。
     * @param tag：下载时设置的tag。
     */
    fun cancelByTag(tag: String): DownloadTask? {
        var downloadTask = findDownloadTaskByTag(tag)
        if (downloadTask == null) {//如果为空，说明当前任务已经被暂停了。此时要到暂停的任务队列中去找。
            downloadTask = findPauseDownload(tag, null)
            if(downloadTask == null){
                this.downloadCallback?.onError(DownloadTask.Build("").setTag(tag).build(),NullPointerException("通过tag没有找到相关的下载任务，请确保当前的下载任务已经设置了tag。"))
                return null
            }
            //下载任务在暂停队列中，说明本地数据库是有保存下载的信息的，此时需要清除数据库中的信息。
            DatabaseManager.deleteBreakPointByTag(tag)
            downloadTask.downloadType = DownloadTask.CANCEL
            this.downloadCallback?.onCancel(downloadTask)
            return downloadTask
        } else {//不为空，说明下载任务还在 Dispatcher 的两个下载任务队列中
            return cancel(downloadTask)
        }
    }

    /**
     * 取消所有下载
     */
    fun cancelAll() {
        val iteratorReady = downloadDispatcher.getReadyDownloadTasks().iterator()
        while (iteratorReady.hasNext()){
            cancel(iteratorReady.next())
        }

        val iteratorRunning = downloadDispatcher.getRunningDownloadTasks().iterator()
        while (iteratorRunning.hasNext()){
            cancel(iteratorRunning.next())
        }

        val iteratorPause = pauseDownloadCaches.iterator()
        while (iteratorPause.hasNext()){
            val pauseDownloadTask = iteratorPause.next()
            this.downloadCallback?.onCancel(pauseDownloadTask)
            DatabaseManager.deleteBreakPointByUrl(Utils.md5(pauseDownloadTask.url))
            iteratorPause.remove()
        }

    }

    //暂停下载时的缓存
    private val pauseDownloadCaches = PriorityBlockingQueue<DownloadTask>()

    /**暂停下载*/
    fun pauseByTag(tag: String) {
        pause(findDownloadTaskByTag(tag))
    }

    /**暂停下载*/
    fun pauseByUrl(url: String) {
        pause(findDownloadTaskByUrl(url))
    }

    /**
     * 暂停全部下载
     */
    fun pauseAll(){
        val iteratorReady = downloadDispatcher.getReadyDownloadTasks().iterator()
        while (iteratorReady.hasNext()){
            pause(iteratorReady.next())
        }

        val iteratorRunning = downloadDispatcher.getRunningDownloadTasks().iterator()
        while (iteratorRunning.hasNext()){
            pause(iteratorRunning.next())
            Log.e("CCC","size: ${downloadDispatcher.getRunningDownloadTasks().size}")
        }
    }

    /**
     *暂停下载
     */
    private fun pause(pauseDownloadTask: DownloadTask?) {
        if(pauseDownloadTask?.isDownLoading() == true){
            //如果支持断点下载，就可以执行暂停操作。如果不支持断点下载，给一个异常
            if (pauseDownloadTask.isSupportBreakpointDownloads) {
                pauseDownloadTask.downloadType = DownloadTask.PAUSE
                downloadDispatcher.cancel(pauseDownloadTask)
                pauseDownloadCaches.add(pauseDownloadTask)
            } else {
                pauseDownloadTask.let { downloadCallback?.onError(it, UnsupportedOperationException("当前下载链接不支持断点下载：${it.url}")) }
            }
        }else{
            pauseDownloadTask?.downloadType = DownloadTask.PAUSE
            downloadDispatcher.cancel(pauseDownloadTask)
            pauseDownloadCaches.add(pauseDownloadTask)
        }

    }


    /**开始下载*/
    fun resumeByTag(tag: String) {
        findPauseDownload(tag, null)?.let {
            downloadDispatcher.enqueue(it)
        }
    }

    /**继续下载*/
    fun resumeByUrl(url: String) {
        findPauseDownload(null, url)?.let {
            downloadDispatcher.enqueue(it)
        }
    }

    /**全部继续下载*/
    fun resumeAll(){
        pauseDownloadCaches.forEach {
            downloadDispatcher.enqueue(it)
        }
        pauseDownloadCaches.clear()
    }

    private fun findPauseDownload(tag: String?, url: String?): DownloadTask? {
        var pauseDownloadCache: DownloadTask? = null
        val iterator = pauseDownloadCaches.iterator()
        while (iterator.hasNext()) {
            val downloadTask = iterator.next()
            if (downloadTask.url == url || downloadTask.tag == tag) {
                iterator.remove()
                pauseDownloadCache = downloadTask
                break
            }
        }
        return pauseDownloadCache
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

    fun getDownloadTaskSize() :Int{
        return downloadDispatcher.getReadyDownloadTasks().size + downloadDispatcher.getRunningDownloadTasks().size
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

        override fun onPause(task: DownloadTask) {
            downloadCallback?.onPause(task)
        }

        override fun onCancel(task: DownloadTask) {
            downloadCallback?.onCancel(task)
        }

        override fun onError(task: DownloadTask?, e: Exception?) {
            downloadCallback?.onError(task, e)
            downloadDispatcher.cancel(task)
        }

        override fun onComplete(task: DownloadTask) {
            downloadCallback?.onComplete(task)
            downloadDispatcher.cancel(task)
        }
    }
}