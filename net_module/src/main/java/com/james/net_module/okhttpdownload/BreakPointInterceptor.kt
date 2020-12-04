package com.james.net_module.okhttpdownload

import com.james.net_module.db.DatabaseManager
import com.james.net_module.utils.Utils
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 断点下载拦截器
 */
class BreakPointInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val newBuilder = request.newBuilder()
        var breakPointInfos = DatabaseManager.findBreakPoint(Utils.md5(request.url().url().toString()))
        if (breakPointInfos.isEmpty()) {//没有保存相关的断点信息，发送Range请求头。
            newBuilder.addHeader("RANGE", "bytes=0-")
        } else {//保存了相关的断点信息，将RANGE和If-Range请求头的信息发送到服务器。验证是否要进行断点下载还是重新开始下载
            var breakPointInfo = breakPointInfos.first()
            newBuilder.addHeader("RANGE", "bytes=${breakPointInfo.startPoint}-")
            newBuilder.addHeader("If-Range","${breakPointInfo.eTag}")
        }
        val newRequest = newBuilder.method(request.method(), request.body())
            .build()
        return chain.proceed(newRequest)
    }
}