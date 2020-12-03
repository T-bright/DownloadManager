package com.james.net_module


interface DownLoadEngine {

    fun startDownload(downloadTask: DownloadTask, callback: DownloadCallback)

    fun cancel(downloadTask: DownloadTask)

    fun cancelAll()

}