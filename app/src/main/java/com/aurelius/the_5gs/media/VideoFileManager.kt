package com.aurelius.the_5gs.media

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class VideoFileManager(private val context: Context) {

    private var fileOutputStream: FileOutputStream? = null
    var currentFilePath: String? = null
        private set
    var spsPpsWritten = false
        private set

    fun prepareNewFile(): Boolean {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val videoFile = File(context.cacheDir, "ar_video_$timestamp.h264")
            currentFilePath = videoFile.absolutePath
            fileOutputStream = FileOutputStream(videoFile)
            spsPpsWritten = false
            Log.i("VideoFileManager", "Video output file created at $currentFilePath")
            true
        } catch (e: IOException) {
            Log.e("VideoFileManager", "Failed to prepare output file", e)
            false
        }
    }

    fun writeSpsPps(sps: ByteArray, pps: ByteArray) {
        try {
            fileOutputStream?.apply {
                write(sps)
                write(pps)
                flush()
            }
            spsPpsWritten = true
            Log.d("VideoFileManager", "SPS and PPS written to file.")
        } catch (e: IOException) {
            Log.e("VideoFileManager", "Failed to write SPS/PPS", e)
        }
    }

    fun writeEncodedFrame(encodedData: ByteArray) {
        try {
            if (!spsPpsWritten) {
                Log.w("VideoFileManager", "Skipping frame — SPS/PPS not written yet")
                return
            }
            fileOutputStream?.write(encodedData)
        } catch (e: IOException) {
            Log.e("VideoFileManager", "Error writing encoded frame", e)
        }
    }

    fun close() {
        if (currentFilePath == null) return
        try {
            fileOutputStream?.write(byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x0A))
            fileOutputStream?.flush()
            fileOutputStream?.close()
            Log.i("VideoFileManager", "File closed: $currentFilePath")
        } catch (e: IOException) {
            Log.e("VideoFileManager", "Failed to close file", e)
        }
        fileOutputStream = null
        currentFilePath = null
        spsPpsWritten = false
    }
}
