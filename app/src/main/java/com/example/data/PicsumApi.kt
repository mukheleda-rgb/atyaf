package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class PicsumPhoto(
    @Json(name = "id") val id: String,
    @Json(name = "author") val author: String,
    @Json(name = "download_url") val downloadUrl: String
)

interface PicsumApi {
    @GET("v2/list")
    suspend fun getPhotos(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30
    ): List<PicsumPhoto>

    companion object {
        val instance: PicsumApi by lazy {
            Retrofit.Builder()
                .baseUrl("https://picsum.photos/")
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(PicsumApi::class.java)
        }
    }
}
