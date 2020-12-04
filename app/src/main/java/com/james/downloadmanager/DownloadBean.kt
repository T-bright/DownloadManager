package com.james.downloadmanager

import com.james.net_module.DownloadTask

data class DownloadBean(
    var url: String,
    var progress: Float = 0.0f,
    var downloadType: Int = DownloadTask.WAIT
) {
    var downloadTypeText: String? = null
        get() {
            when(downloadType){
                DownloadTask.WAIT-> field = "等待下载"
                DownloadTask.START-> field = "正在下载"
                DownloadTask.PAUSE-> field = "暂停下载"
                DownloadTask.CANCEL-> field = "取消下载"
                DownloadTask.ERROR-> field = "下载错误"
                DownloadTask.COMPLETE-> field = "下载完成"
            }
            return field
        }

    var isDownloadIng :Boolean = false
        get() {
            return downloadType == DownloadTask.START
        }
}