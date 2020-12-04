package com.james.net_module

import kotlin.Exception

interface DownloadCallback {

    //等待中
    fun onWait(task: DownloadTask)

    //开始下载
    fun onStart(task: DownloadTask)

    //下载进度
    fun onProgress(task: DownloadTask, currentOffset: Long, totalLength: Long)

    //暂停下载的回调。如果当前下载任务不支持断点下载，则抛出异常
    fun onPause(task: DownloadTask, e: Exception?)

    //取消下载
    fun onCancel(task: DownloadTask)

    //下载出错了
    fun onError(task: DownloadTask, e: Exception?)

    //下载完成
    fun onComplete(task: DownloadTask)
}