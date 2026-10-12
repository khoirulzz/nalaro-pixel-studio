package com.nalaro.pixelstudio

import com.nalaro.pixelstudio.data.models.ManifestResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test

class ManifestParsingTest {

    private val jsonSample = """
    {
      "schema_version": 1,
      "manifest_version": "1.0.0",
      "repository": "nalaro/pixel-studio-models",
      "updated_at": "2026-10-12T12:00:00Z",
      "models": [
        {
          "id": "birefnet-general-lite",
          "name": "BiRefNet General Lite",
          "task": "background_removal",
          "version": "1.0.0",
          "revision": "e9b5f8a1c7204dbdf23a597a7a3b3780517865f1",
          "filename": "birefnet_general_lite_quant.onnx",
          "download_url": "https://huggingface.co/nalaro/pixel-studio-models/resolve/e9b5f8a1c7204dbdf23a597a7a3b3780517865f1/birefnet_general_lite_quant.onnx",
          "size_bytes": 45180416,
          "sha256": "3a52f970d4f3b7d1e8c92a6b8e3a4f5d6c7b8a9e0f1a2b3c4d5e6f7a8b9c0d1e",
          "min_app_version_code": 1,
          "ram_requirement_mb": 1024,
          "runtime_compatibility": ["cpu", "nnapi"],
          "license": "Apache-2.0",
          "license_url": "https://huggingface.co/nalaro/pixel-studio-models/blob/main/LICENSE",
          "input_resolution": [1024, 1024],
          "is_recommended": true,
          "description": "Quantized INT8 high-speed general background removal."
        }
      ]
    }
    """.trimIndent()

    @Test
    fun testParseHuggingFaceManifest() {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(ManifestResponse::class.java)

        val manifest = adapter.fromJson(jsonSample)
        assertNotNull(manifest)
        assertEquals(1, manifest!!.schemaVersion)
        assertEquals("nalaro/pixel-studio-models", manifest.repository)
        assertEquals(1, manifest.models.size)

        val model = manifest.models[0]
        assertEquals("birefnet-general-lite", model.id)
        assertEquals("e9b5f8a1c7204dbdf23a597a7a3b3780517865f1", model.revision)
        assertEquals(45180416L, model.sizeBytes)
        assertEquals("Apache-2.0", model.license)
        assertTrue(model.downloadUrl.contains("/resolve/e9b5f8a1c7204dbdf23a597a7a3b3780517865f1/"))
        assertTrue(model.isRecommended)
    }
}
