package com.nalaro.pixelstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.MessageDigest

class Sha256ChecksumTest {

    private fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testChecksumCalculationAndTamperDetection() {
        val sampleData = "Nalaro Pixel Studio ONNX Model Binary Payload".toByteArray(Charsets.UTF_8)
        val hash1 = calculateSha256(sampleData)

        // Same content must give identical hash
        val hash2 = calculateSha256(sampleData)
        assertEquals(hash1, hash2)

        // Altered content must detect mismatch
        val tamperedData = "Nalaro Pixel Studio ONNX Model Binary Payload (Tampered)".toByteArray(Charsets.UTF_8)
        val hashTampered = calculateSha256(tamperedData)
        assertNotEquals(hash1, hashTampered)
    }
}
