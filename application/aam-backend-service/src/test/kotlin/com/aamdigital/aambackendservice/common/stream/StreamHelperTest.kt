package com.aamdigital.aambackendservice.common.stream

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.Collections

class StreamHelperTest {
    companion object {
        private const val BUFFER_LENGTH = 4096

        /**
         * Devanagari text whose characters each encode as three UTF-8 bytes, e.g. "१" (U+0967) as
         * `E0 A5 A7`. A read boundary inside such a sequence turns one character into three
         * replacement characters, which is how the corruption became visible in report results.
         */
        private const val MULTI_BYTE_VALUE = "१ तासापेक्षा जास्त"

        private const val REPLACEMENT_CHARACTER = '�'
    }

    @Test
    fun `should copy bytes unchanged when a multi-byte character straddles the buffer boundary`() {
        // Given
        val input = payloadSplittingMultiByteCharacterAt(BUFFER_LENGTH)
        val outputStream = ByteArrayOutputStream()

        // When
        handleInputStreamToOutputStream(outputStream, ByteArrayInputStream(input), BUFFER_LENGTH)

        // Then
        assertThat(outputStream.toByteArray()).isEqualTo(input)
    }

    @Test
    fun `should not replace characters that straddle the buffer boundary`() {
        // Given
        val input = payloadSplittingMultiByteCharacterAt(BUFFER_LENGTH)
        val outputStream = ByteArrayOutputStream()

        // When
        handleInputStreamToOutputStream(outputStream, ByteArrayInputStream(input), BUFFER_LENGTH)

        // Then
        assertThat(outputStream.toString(Charsets.UTF_8))
            .doesNotContain(REPLACEMENT_CHARACTER.toString())
            .endsWith(MULTI_BYTE_VALUE)
    }

    @Test
    fun `should copy bytes unchanged when the source returns short reads`() {
        // Given: HTTP response streams and SequenceInputStream both hand back fewer bytes than
        // requested, so multi-byte characters get split regardless of the buffer size.
        val input = MULTI_BYTE_VALUE.toByteArray()
        val outputStream = ByteArrayOutputStream()

        // When
        handleInputStreamToOutputStream(outputStream, singleByteReadsOf(input), BUFFER_LENGTH)

        // Then
        assertThat(outputStream.toByteArray()).isEqualTo(input)
    }

    @Test
    fun `should copy bytes unchanged when a multi-byte character is split across concatenated streams`() {
        // Given: report results are assembled from a SequenceInputStream of query responses, which
        // never reads across the boundary between two sub-streams
        val input = MULTI_BYTE_VALUE.toByteArray()
        val splitInsideCharacter = 1
        val concatenated =
            SequenceInputStream(
                Collections.enumeration(
                    listOf<InputStream>(
                        ByteArrayInputStream(input, 0, splitInsideCharacter),
                        ByteArrayInputStream(input, splitInsideCharacter, input.size - splitInsideCharacter)
                    )
                )
            )
        val outputStream = ByteArrayOutputStream()

        // When
        handleInputStreamToOutputStream(outputStream, concatenated, BUFFER_LENGTH)

        // Then
        assertThat(outputStream.toByteArray()).isEqualTo(input)
    }

    @Test
    fun `should copy an empty stream without writing anything`() {
        // Given
        val outputStream = ByteArrayOutputStream()

        // When
        handleInputStreamToOutputStream(outputStream, ByteArrayInputStream(ByteArray(0)), BUFFER_LENGTH)

        // Then
        assertThat(outputStream.toByteArray()).isEmpty()
    }

    /**
     * Build a payload whose first multi-byte character starts one byte before [offset], so that a
     * read of [offset] bytes ends in the middle of that character's UTF-8 sequence.
     */
    private fun payloadSplittingMultiByteCharacterAt(offset: Int): ByteArray {
        val padding = ByteArray(offset - 1) { 'x'.code.toByte() }
        return padding + MULTI_BYTE_VALUE.toByteArray()
    }

    /**
     * Wrap [content] in a stream that never returns more than a single byte per read.
     */
    private fun singleByteReadsOf(content: ByteArray): InputStream =
        object : InputStream() {
            private val delegate = ByteArrayInputStream(content)

            override fun read(): Int = delegate.read()

            override fun read(
                b: ByteArray,
                off: Int,
                len: Int
            ): Int = delegate.read(b, off, minOf(len, 1))
        }
}
