package com.example.data.remote

import com.example.data.model.CityOption
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class GeocodingResponse(
    @Json(name = "results") val results: List<GeocodingResult>?
)

@JsonClass(generateAdapter = true)
data class GeocodingResult(
    @Json(name = "name") val name: String,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "country") val country: String?,
    @Json(name = "admin1") val admin1: String?
)

interface GeocodingApiService {
    @GET("v1/search")
    suspend fun searchCities(
        @Query("name") query: String,
        @Query("count") count: Int = 12,
        @Query("language") language: String = "ru",
        @Query("format") format: String = "json"
    ): GeocodingResponse
}

object CuratedCities {
    val presets: List<CityOption> = listOf(
        CityOption("Москва", "Россия", "Москва", 55.7558, 37.6173),
        CityOption("Санкт-Петербург", "Россия", "Санкт-Петербург", 59.9343, 30.3351),
        CityOption("Казань", "Россия", "Татарстан", 55.7961, 49.1064),
        CityOption("Екатеринбург", "Россия", "Свердловская обл.", 56.8389, 60.6057),
        CityOption("Новосибирск", "Россия", "Новосибирская обл.", 55.0084, 82.9357),
        CityOption("Сочи", "Россия", "Краснодарский край", 43.5855, 39.7231),
        CityOption("Владивосток", "Россия", "Приморский край", 43.1155, 131.8855),
        CityOption("Минск", "Беларусь", "Минск", 53.9006, 27.5590),
        CityOption("Алматы", "Казахстан", "Алматы", 43.2220, 76.8512)
    )
}
