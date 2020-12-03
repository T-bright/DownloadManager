package com.james.net_module.utils

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object Utils {


    fun isUriContentScheme(uri: Uri): Boolean {
        return uri.scheme == ContentResolver.SCHEME_CONTENT
    }

    fun isUriFileScheme(uri: Uri): Boolean {
        return uri.scheme == ContentResolver.SCHEME_FILE
    }

    fun file2Uri(file: File): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val authority = NetModuleProvider.mContext?.packageName + ".netmodule.provider"
            FileProvider.getUriForFile(NetModuleProvider.mContext!!, authority, file)
        } else {
            Uri.fromFile(file)
        }
    }

    fun uri2FileName(contentUri: Uri): String? {
        val resolver = NetModuleProvider.mContext?.contentResolver
        val cursor = resolver?.query(contentUri, null, null, null, null)
        return if (cursor != null) {
            try {
                cursor.moveToFirst()
                cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            } finally {
                cursor.close()
            }
        } else null
    }

    fun md5(str: String): String {
        var result = ""
        if (str.isEmpty()) return result
        try {
            val md5 = MessageDigest.getInstance("MD5")
            val bytes = md5.digest(str.toByteArray())
            val sb = StringBuilder()
            for (b in bytes) {
                var temp = Integer.toHexString(b.toInt() and 0xff)
                if (temp.length == 1) {
                    temp = "0$temp"
                }
                sb.append(temp)
            }
            result = sb.toString()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }
        return result
    }


}