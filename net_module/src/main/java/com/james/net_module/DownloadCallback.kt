package com.james.net_module

import kotlin.Exception

interface DownloadCallback {

    //等待中
    fun onWait(task: DownloadTask)

    //开始下载
    fun onStart(task: DownloadTask)

    //下载进度
    fun onProgress(task: DownloadTask, currentOffset: Long, totalLength: Long)

    //暂停下载的回调
    fun onPause(task: DownloadTask)

    //取消下载
    fun onCancel(task: DownloadTask)

    /**
     * 下载出错。错误来源有几下几个方面：
     *
     * 1、暂停下载。如果当前下载任务不支持断点下载，此时如果用户操作了暂停下载，就会回调此方法。这里需要开发人员自己处理相关逻辑。详细请看[DownloadTask.isSupportBreakpointDownloads]
     *
     * 2、取消下载。如果之前没有设置tag，取消下载时，通过tag取消，就会回调此方法。这里需要开发人员自己注意。
     *
     * 3、http下载时出现的错误信息回调。如网络连接失败等
     */
    fun onError(task: DownloadTask?, e: Exception?)

    //下载完成
    fun onComplete(task: DownloadTask)
}