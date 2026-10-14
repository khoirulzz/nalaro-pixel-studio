package com.nalaro.pixelstudio.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

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

/**
 * Zero-reflection streaming Moshi adapter for [ManifestResponse] and [ModelMetadataDto].
 * Guarantees fast, crash-free JSON parsing even under aggressive R8 full-mode shrinking.
 */
object ManifestJsonAdapter : JsonAdapter<ManifestResponse>() {

    val FACTORY = object : Factory {
        override fun create(
            type: Type,
            annotations: Set<Annotation>,
            moshi: Moshi
        ): JsonAdapter<*>? {
            if (annotations.isNotEmpty()) return null
            return when (Types.getRawType(type)) {
                ManifestResponse::class.java -> ManifestJsonAdapter
                ModelMetadataDto::class.java -> ModelDtoJsonAdapter
                else -> null
            }
        }
    }

    override fun fromJson(reader: JsonReader): ManifestResponse {
        var schemaVersion = 1
        var manifestVersion = "1.0.0"
        var repository = "nalaro/pixel-studio-models"
        var updatedAt = ""
        val models = mutableListOf<ModelMetadataDto>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "schema_version" -> schemaVersion = reader.nextIntOrDefault(1)
                "manifest_version" -> manifestVersion = reader.nextStringOrDefault("1.0.0")
                "repository" -> repository = reader.nextStringOrDefault("nalaro/pixel-studio-models")
                "updated_at" -> updatedAt = reader.nextStringOrDefault("")
                "models" -> {
                    if (reader.peek() == JsonReader.Token.NULL) {
                        reader.nextNull<Unit>()
                    } else {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            ModelDtoJsonAdapter.fromJson(reader)?.let { models.add(it) }
                        }
                        reader.endArray()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return ManifestResponse(
            schemaVersion = schemaVersion,
            manifestVersion = manifestVersion,
            repository = repository,
            updatedAt = updatedAt,
            models = models
        )
    }

    override fun toJson(writer: JsonWriter, value: ManifestResponse?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        writer.name("schema_version").value(value.schemaVersion)
        writer.name("manifest_version").value(value.manifestVersion)
        writer.name("repository").value(value.repository)
        writer.name("updated_at").value(value.updatedAt)
        writer.name("models")
        writer.beginArray()
        for (model in value.models) {
            ModelDtoJsonAdapter.toJson(writer, model)
        }
        writer.endArray()
        writer.endObject()
    }
}

object ModelDtoJsonAdapter : JsonAdapter<ModelMetadataDto>() {

    override fun fromJson(reader: JsonReader): ModelMetadataDto {
        var id = ""
        var name = ""
        var task = "background_removal"
        var version = "1.0.0"
        var revision = ""
        var filename = ""
        var downloadUrl = ""
        var sizeBytes = 0L
        var sha256 = ""
        var minAppVersionCode = 1
        var ramRequirementMb = 1024
        val runtimeCompatibility = mutableListOf<String>()
        var hasRuntimeCompatibility = false
        var license = "Apache-2.0"
        var licenseUrl = ""
        val inputResolution = mutableListOf<Int>()
        var hasInputResolution = false
        var isRecommended = false
        var description = ""

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextStringOrDefault("")
                "name" -> name = reader.nextStringOrDefault("")
                "task" -> task = reader.nextStringOrDefault("background_removal")
                "version" -> version = reader.nextStringOrDefault("1.0.0")
                "revision" -> revision = reader.nextStringOrDefault("")
                "filename" -> filename = reader.nextStringOrDefault("")
                "download_url" -> downloadUrl = reader.nextStringOrDefault("")
                "size_bytes" -> sizeBytes = reader.nextLongOrDefault(0L)
                "sha256" -> sha256 = reader.nextStringOrDefault("")
                "min_app_version_code" -> minAppVersionCode = reader.nextIntOrDefault(1)
                "ram_requirement_mb" -> ramRequirementMb = reader.nextIntOrDefault(1024)
                "runtime_compatibility" -> {
                    if (reader.peek() == JsonReader.Token.NULL) {
                        reader.nextNull<Unit>()
                    } else {
                        hasRuntimeCompatibility = true
                        reader.beginArray()
                        while (reader.hasNext()) {
                            runtimeCompatibility.add(reader.nextStringOrDefault("cpu"))
                        }
                        reader.endArray()
                    }
                }
                "license" -> license = reader.nextStringOrDefault("Apache-2.0")
                "license_url" -> licenseUrl = reader.nextStringOrDefault("")
                "input_resolution" -> {
                    if (reader.peek() == JsonReader.Token.NULL) {
                        reader.nextNull<Unit>()
                    } else {
                        hasInputResolution = true
                        reader.beginArray()
                        while (reader.hasNext()) {
                            inputResolution.add(reader.nextIntOrDefault(1024))
                        }
                        reader.endArray()
                    }
                }
                "is_recommended" -> isRecommended = reader.nextBooleanOrDefault(false)
                "description" -> description = reader.nextStringOrDefault("")
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return ModelMetadataDto(
            id = id,
            name = name,
            task = task,
            version = version,
            revision = revision,
            filename = filename,
            downloadUrl = downloadUrl,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            minAppVersionCode = minAppVersionCode,
            ramRequirementMb = ramRequirementMb,
            runtimeCompatibility = if (hasRuntimeCompatibility) runtimeCompatibility else listOf("cpu", "nnapi"),
            license = license,
            licenseUrl = licenseUrl,
            inputResolution = if (hasInputResolution) inputResolution else listOf(1024, 1024),
            isRecommended = isRecommended,
            description = description
        )
    }

    override fun toJson(writer: JsonWriter, value: ModelMetadataDto?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        writer.name("id").value(value.id)
        writer.name("name").value(value.name)
        writer.name("task").value(value.task)
        writer.name("version").value(value.version)
        writer.name("revision").value(value.revision)
        writer.name("filename").value(value.filename)
        writer.name("download_url").value(value.downloadUrl)
        writer.name("size_bytes").value(value.sizeBytes)
        writer.name("sha256").value(value.sha256)
        writer.name("min_app_version_code").value(value.minAppVersionCode)
        writer.name("ram_requirement_mb").value(value.ramRequirementMb)
        writer.name("runtime_compatibility")
        writer.beginArray()
        for (item in value.runtimeCompatibility) {
            writer.value(item)
        }
        writer.endArray()
        writer.name("license").value(value.license)
        writer.name("license_url").value(value.licenseUrl)
        writer.name("input_resolution")
        writer.beginArray()
        for (dim in value.inputResolution) {
            writer.value(dim)
        }
        writer.endArray()
        writer.name("is_recommended").value(value.isRecommended)
        writer.name("description").value(value.description)
        writer.endObject()
    }
}

private fun JsonReader.nextStringOrDefault(default: String): String {
    return if (peek() == JsonReader.Token.NULL) {
        nextNull<Unit>()
        default
    } else {
        nextString()
    }
}

private fun JsonReader.nextIntOrDefault(default: Int): Int {
    return if (peek() == JsonReader.Token.NULL) {
        nextNull<Unit>()
        default
    } else {
        nextInt()
    }
}

private fun JsonReader.nextLongOrDefault(default: Long): Long {
    return if (peek() == JsonReader.Token.NULL) {
        nextNull<Unit>()
        default
    } else {
        nextLong()
    }
}

private fun JsonReader.nextBooleanOrDefault(default: Boolean): Boolean {
    return if (peek() == JsonReader.Token.NULL) {
        nextNull<Unit>()
        default
    } else {
        nextBoolean()
    }
}
