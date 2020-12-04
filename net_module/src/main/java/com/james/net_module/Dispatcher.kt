package com.james.net_module

import android.util.Log
import java.util.*

/**
 * 下载任务的分发管理类
 */
class Dispatcher constructor(maxDownloadCount: Int = 5) {

    /**准备下载的任务队列*/
    private val readyDownloadTasks = ArrayDeque<DownloadTask>()

    /**正在下载的任务队列*/
    private val runningDownloadTasks = ArrayDeque<DownloadTask>()

    /**
     * 将准备下载的任务队列[runningDownloadTasks]中的任务添加到
     * 正在下载的任务队列[runningDownloadTasks] 中时的回调，因为此时要开始执行下载任务了
     */
    var startDownload: ((downloadTask: DownloadTask) -> Unit)? = null

    /**
     * 取消某一个下载任务的回调
     */
    var cancelDownload: ((downloadTask: DownloadTask) -> Unit)? = null

    @Volatile
    private var maxDownloadCount: Int = 5 //最大下载的个数

    init {
        this.maxDownloadCount = maxDownloadCount
    }

    /**
     * 将单个任务添加到队列里面
     * @param downloadTask ：下载任务
     */
    fun enqueue(downloadTask: DownloadTask) {
        synchronized(this) {
            if (findDownloadTaskByUrl(downloadTask.url) == null) {
                readyDownloadTasks.add(downloadTask)
                promote()
            }
        }
    }

    /**
     * 将多个任务添加到队列里面
     * @param downloadTasks ：下载任务
     */
    fun enqueue(downloadTasks: List<DownloadTask>) {
        synchronized(this) {
            downloadTasks.forEach {
                if (findDownloadTaskByUrl(it.url) == null) {
                    readyDownloadTasks.add(it)
                }
            }
            promote()
        }
    }

    /**
     * 每次添加或者删除任务都要重新处理下任务队列
     */
    private fun promote() {
        val iterator: MutableIterator<DownloadTask> = readyDownloadTasks.iterator()
        while (iterator.hasNext()) {
            val downloadTask = iterator.next()
            if (runningDownloadTasks.size >= maxDownloadCount) break
            iterator.remove()
            runningDownloadTasks.add(downloadTask)
            startDownload?.invoke(downloadTask)
        }
    }


    fun cancel(downloadTask: DownloadTask?): DownloadTask? {
        synchronized(this) {
            if (downloadTask == null) return null

            var needCancelTask = finish(runningDownloadTasks, downloadTask)

            if (needCancelTask == null) {
                needCancelTask = finish(readyDownloadTasks, downloadTask)
            }
            if (needCancelTask != null) {
                cancelAfterPromote(needCancelTask)
            }
            return needCancelTask
        }
    }

    private fun finish(tasks: Deque<DownloadTask>, task: DownloadTask): DownloadTask? {
        return if (tasks.remove(task)) task else null
    }

    /**
     * 这个方法的意义是取消[runningDownloadTasks]和[readyDownloadTasks]队列中的下载任务。
     * 如果当前的下载任务正在下载，需要将这个操作告诉 [DownloadManager] 以便通知 下载引擎[DownLoadEngine]去做取消下载任务
     */
    private fun cancelAfterPromote(needCancelTask: DownloadTask) {
        promote()
        Log.e("CCC","dispatcher type :${needCancelTask.downloadType}")
        //下载完成了，
        if(needCancelTask.downloadType != DownloadTask.COMPLETE && needCancelTask.downloadType != DownloadTask.ERROR){
            /**
             * 这行代码的意义是：通知 下载引擎 [OkHttpDownload] 取消下载任务。
             * 如果是下载出错或者已经下载完成了，就不需要去通知了。 因为下载引擎那已经取消了相关的下载任务。
             */
            cancelDownload?.invoke(needCancelTask)
        }
    }

    fun cancelAll() {
        readyDownloadTasks.clear()
        val iterator: MutableIterator<DownloadTask> = runningDownloadTasks.iterator()
        while (iterator.hasNext()) {
            val downloadTask = iterator.next()
            iterator.remove()
            if (downloadTask.isDownLoading()) {
                cancelDownload?.invoke(downloadTask)
            }
        }
    }

    fun findDownloadTaskByUrl(url: String): DownloadTask? {
        synchronized(this) {
            var findTask = find(runningDownloadTasks, null, url)

            if (findTask == null) {
                findTask = find(readyDownloadTasks, null, url)
            }
            return findTask
        }
    }

    fun findDownloadTaskByTag(tag: String): DownloadTask? {
        synchronized(this) {
            var findTask = find(runningDownloadTasks, tag, null)

            if (findTask == null) {
                findTask = find(readyDownloadTasks, tag, null)
            }
            return findTask
        }
    }

    private fun find(tasks: Deque<DownloadTask>, tag: String?, url: String?): DownloadTask? {
        var needRemoveDownloadTask: DownloadTask? = null
        tasks.forEach {
            if (it.tag == tag) needRemoveDownloadTask = it
            if (it.url == url) needRemoveDownloadTask = it
        }
        return needRemoveDownloadTask
    }


    fun destroy() {
        cancelAll()
        startDownload = null
        cancelDownload = null
    }
}