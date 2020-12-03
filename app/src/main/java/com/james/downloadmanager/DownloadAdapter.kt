package com.james.downloadmanager

import android.annotation.SuppressLint
import android.widget.ProgressBar
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder


class DownloadAdapter constructor(var datas: List<DownloadBean>) :
    BaseQuickAdapter<DownloadBean, BaseViewHolder>(R.layout.item_download, datas) {

    fun setProgress(index: Int, progress: Float, downloadType: Int) {
        var dataBean = datas[index]
        dataBean.progress = progress
        dataBean.downloadType = downloadType
        notifyItemChanged(index,"progress")
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: BaseViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            var databean = datas[position]
            holder.getView<ProgressBar>(R.id.progressBar).progress = databean.progress.toInt()
            holder.getView<TextView>(R.id.tvProgress).text = "${databean.progress}%"
            holder.getView<TextView>(R.id.tvDownloadType).text = "状态：${databean.downloadTypeText}"
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun convert(helper: BaseViewHolder, item: DownloadBean?) {
        var databean = datas[helper.layoutPosition]
        helper.addOnClickListener(R.id.tvDownload, R.id.tvCancel)
        helper.getView<ProgressBar>(R.id.progressBar).progress = databean.progress.toInt()
        helper.getView<TextView>(R.id.tvProgress).text = "${databean.progress}%"
        helper.getView<TextView>(R.id.tvDownloadType).text = "状态：${databean.downloadTypeText}"
    }

}