package com.nalaro.pixelstudio.data.network

import com.nalaro.pixelstudio.data.models.ManifestResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface HuggingFaceApi {

    @GET
    suspend fun getManifest(@Url manifestUrl: String): Response<ManifestResponse>

    @Streaming
    @GET
    suspend fun downloadModelStream(
        @Url fileUrl: String,
        @Header("Range") rangeHeader: String? = null
    ): Response<ResponseBody>

    companion object {
        const val DEFAULT_BASE_URL = "https://huggingface.co/"
        const val DEFAULT_REPO = "nalaro/pixel-studio-models"
        const val DEFAULT_MANIFEST_PATH = "raw/main/models.json"

        fun getManifestUrl(repoId: String = DEFAULT_REPO, path: String = DEFAULT_MANIFEST_PATH): String {
            return "https://huggingface.co/$repoId/$path"
        }

        fun createClient(): HuggingFaceApi {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor(loggingInterceptor)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(DEFAULT_BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            return retrofit.create(HuggingFaceApi::class.java)
        }
    }
}
