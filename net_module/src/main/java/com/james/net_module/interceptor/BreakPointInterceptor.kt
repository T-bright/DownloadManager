package com.james.net_module.interceptor

import com.james.net_module.db.DatabaseManager
import com.james.net_module.utils.Utils
import okhttp3.Interceptor
import okhttp3.Response

class BreakPointInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val newBuilder = request.newBuilder()
        var breakPointInfos = DatabaseManager.findBreakPoint(Utils.md5(request.url().url().toString()))
        if (breakPointInfos.isEmpty()) {
            newBuilder.addHeader("RANGE", "bytes=0-")
        } else {
            var breakPointInfo = breakPointInfos.first()
            newBuilder.addHeader("RANGE", "bytes=${breakPointInfo.startPoint}-")
            newBuilder.addHeader("If-Range","${breakPointInfo.eTag}")
        }
        val newRequest = newBuilder.method(request.method(), request.body())
            .build()
        return chain.proceed(newRequest)
    }
}