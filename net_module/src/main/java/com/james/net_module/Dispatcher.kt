package com.james.net_module

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
     * 正在下载的任务队列[runningDownloadTasks] 中时的回调
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

    fun cancelByUrl(url: String): DownloadTask {
        synchronized(this) {
            var needCancelTask = finishByUrl(runningDownloadTasks, url)
            if (needCancelTask == null) {
                needCancelTask = finishByUrl(readyDownloadTasks, url)
            }
            if (needCancelTask == null) {
                throw AssertionError("当前 DownloadTask 不在下载任务队列中")
            }
            cancelAfterPromote(needCancelTask)
            return needCancelTask
        }
    }

    fun cancelByTag(tag: String): DownloadTask {
        synchronized(this) {
            var needCancelTask = finishByTag(runningDownloadTasks, tag)

            if (needCancelTask == null) {
                needCancelTask = finishByTag(readyDownloadTasks, tag)
            }

            if (needCancelTask == null) {
                throw AssertionError("当前 DownloadTask 不在下载任务队列中")
            }

            cancelAfterPromote(needCancelTask)
            return needCancelTask
        }
    }

    fun cancel(downloadTask: DownloadTask): DownloadTask {
        synchronized(this) {
            var needCancelTask = finish(runningDownloadTasks, downloadTask)

            if (needCancelTask == null) {
                needCancelTask = finish(readyDownloadTasks, downloadTask)
            }

            if (needCancelTask == null) {
                throw AssertionError("当前 DownloadTask 不在下载任务队列中")
            }

            cancelAfterPromote(needCancelTask)
            return needCancelTask
        }
    }

    /**
     * 这个方法的意义是取消[runningDownloadTasks]和[readyDownloadTasks]队列中的下载任务。
     * 如果当前的下载任务正在下载，需要将这个操作告诉 [DownloadManager] 以便通知 下载引擎[DownLoadEngine]去做取消下载任务
     */
    private fun cancelAfterPromote(needCancelTask: DownloadTask) {
        promote()
        if (needCancelTask.isDownLoading()) {
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

    /**
     * 这个方法判断当前的下载任务十分已经加入到了队列中
     */
    fun containDownloadTask(downloadTask: DownloadTask): Boolean {
        return runningDownloadTasks.contains(downloadTask) || readyDownloadTasks.contains(
            downloadTask
        )
    }

    fun findDownloadTask(downloadTask: DownloadTask): DownloadTask? {
        return if (containDownloadTask(downloadTask)) downloadTask else null
    }

    fun findDownloadTaskByUrl(url: String): DownloadTask? {
        return find(runningDownloadTasks, null, url)?.let {
            find(readyDownloadTasks, null, url)
        }
    }

    fun findDownloadTaskByTag(tag: String): DownloadTask? {
        return find(runningDownloadTasks, tag, null)?.let {
            find(readyDownloadTasks, tag, null)
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

    private fun finishByTag(tasks: Deque<DownloadTask>, tag: String): DownloadTask? {
        var needRemoveDownloadTask: DownloadTask? = null
        tasks.forEach {
            if (it.tag == tag) {
                needRemoveDownloadTask = finish(tasks, it)
            }
        }
        return needRemoveDownloadTask
    }

    private fun finishByUrl(tasks: Deque<DownloadTask>, url: String): DownloadTask? {
        var needRemoveDownloadTask: DownloadTask? = null
        tasks.forEach {
            if (it.url == url) {
                needRemoveDownloadTask = finish(tasks, it)
            }
        }
        return needRemoveDownloadTask
    }

    private fun finish(tasks: Deque<DownloadTask>, task: DownloadTask): DownloadTask? {
        return if (tasks.remove(task)) task else null
    }

    fun destroy() {
        cancelAll()
        startDownload = null
        cancelDownload = null
    }
}