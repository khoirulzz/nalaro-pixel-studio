package com.nalaro.pixelstudio.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ManifestResponse(
    @Json(name = "schema_version") val schemaVersion: Int = 1,
    @Json(name = "manifest_version") val manifestVersion: String = "1.0.0",
    @Json(name = "repository") val repository: String = "nalaro/pixel-studio-models",
    @Json(name = "updated_at") val updatedAt: String = "",
    @Json(name = "models") val models: List<ModelMetadataDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ModelMetadataDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "task") val task: String = "background_removal",
    @Json(name = "version") val version: String,
    @Json(name = "revision") val revision: String,
    @Json(name = "filename") val filename: String,
    @Json(name = "download_url") val downloadUrl: String,
    @Json(name = "size_bytes") val sizeBytes: Long,
    @Json(name = "sha256") val sha256: String,
    @Json(name = "min_app_version_code") val minAppVersionCode: Int = 1,
    @Json(name = "ram_requirement_mb") val ramRequirementMb: Int = 1024,
    @Json(name = "runtime_compatibility") val runtimeCompatibility: List<String> = listOf("cpu", "nnapi"),
    @Json(name = "license") val license: String = "Apache-2.0",
    @Json(name = "license_url") val licenseUrl: String = "",
    @Json(name = "input_resolution") val inputResolution: List<Int> = listOf(1024, 1024),
    @Json(name = "is_recommended") val isRecommended: Boolean = false,
    @Json(name = "description") val description: String = ""
)
