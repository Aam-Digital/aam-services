package com.aamdigital.aambackendservice.common.stream

import java.io.InputStream
import java.io.OutputStream

/**
 * Copy an InputStream into an OutputStream verbatim.
 *
 * The payloads passed through here are already encoded (UTF-8 JSON from SQS and CouchDb), so they
 * must be relayed as bytes. Decoding each read chunk on its own would corrupt every multi-byte
 * character that straddles a read boundary: its bytes are then split across two chunks and each
 * half decodes to replacement characters. Reads return short far more often than the buffer size
 * suggests - HTTP response streams split at network chunk boundaries and SequenceInputStream never
 * reads across its sub-streams - so this affects ordinary payloads, not just very large ones.
 */
fun handleInputStreamToOutputStream(
    outputStream: OutputStream,
    inputStream: InputStream,
    byteArrayBufferLength: Int = 4096
) {
    inputStream.copyTo(outputStream, byteArrayBufferLength)
    outputStream.flush()
}
