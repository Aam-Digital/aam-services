package com.aamdigital.aambackendservice.common.domain

import com.aamdigital.aambackendservice.common.error.AamErrorCode
import java.io.InputStream

enum class FileStorageError : AamErrorCode {
    IO_ERROR,
}

interface FileStorage {
    fun storeFile(
        path: String,
        fileName: String,
        file: InputStream,
    )

    fun fetchFile(
        path: String,
        fileName: String,
    ): InputStream
}
