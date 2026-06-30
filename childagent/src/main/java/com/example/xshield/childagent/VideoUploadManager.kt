package com.example.xshield.childagent

import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

object VideoUploadManager {

    fun uploadVideoAndThumbnail(videoFile: File, thumbFile: File): Pair<String, String>? {
        val serverUrl = "https://chiranjeevi.skillsupriselab.com/upload_video.php"
        val boundary = "----WebKitFormBoundary" + System.currentTimeMillis()

        val connection = URL(serverUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.doInput = true
        connection.useCaches = false
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        try {
            val outputStream = DataOutputStream(connection.outputStream)

            // Video Part
            outputStream.writeBytes("--$boundary\r\n")
            outputStream.writeBytes("Content-Disposition: form-data; name=\"video\"; filename=\"${videoFile.name}\"\r\n")
            outputStream.writeBytes("Content-Type: video/mp4\r\n\r\n")

            var fileInputStream = FileInputStream(videoFile)
            var buffer = ByteArray(4096)
            var bytesRead: Int
            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            fileInputStream.close()

            // Thumbnail Part
            outputStream.writeBytes("\r\n--$boundary\r\n")
            outputStream.writeBytes("Content-Disposition: form-data; name=\"thumbnail\"; filename=\"${thumbFile.name}\"\r\n")
            outputStream.writeBytes("Content-Type: image/jpeg\r\n\r\n")

            fileInputStream = FileInputStream(thumbFile)
            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            fileInputStream.close()

            // End boundary
            outputStream.writeBytes("\r\n--$boundary--\r\n")
            outputStream.flush()
            outputStream.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = connection.inputStream.bufferedReader()
                val responseStr = reader.readText()
                reader.close()

                val json = JSONObject(responseStr)
                if (json.optString("status") == "success") {
                    val vUrl = json.getString("videoUrl")
                    val tUrl = json.getString("thumbnailUrl")
                    return Pair(vUrl.replace("\\/", "/"), tUrl.replace("\\/", "/"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection.disconnect()
        }
        return null
    }
}
