package com.aamdigital.aambackendservice.stream

import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Write an InputStream into a UTF_8 OutputStream
 */
fun handleInputStreamToOutputStream(
    outputStream: OutputStream,
    inputStream: InputStream,
    byteArrayBufferLength: Int = 4096
) {
    OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
        val buffer = ByteArray(byteArrayBufferLength)
        var bytesRead: Int
        while ((inputStream.read(buffer).also { bytesRead = it }) != -1) {
            if (bytesRead > 0) {
                writer.write(String(buffer, 0, bytesRead, Charsets.UTF_8))
            }
        }
    }
}
