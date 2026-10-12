package com.nalaro.pixelstudio

import com.nalaro.pixelstudio.core.compression.ImageFormat
import com.nalaro.pixelstudio.core.converter.FormatConverter
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatConverterTest {

    @Test
    fun testBatchRenamePattern() {
        val converter = FormatConverter()

        val filename = converter.generateBatchFilename("banner_holiday.png", ImageFormat.WEBP, 1)
        assertEquals("banner_holiday-webp-1.webp", filename)

        val jpegFilename = converter.generateBatchFilename("product_photo.jpg", ImageFormat.PNG, 3)
        assertEquals("product_photo-png-3.png", jpegFilename)
    }
}
