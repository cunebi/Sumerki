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
    @Json(name = "admin1") val admin1: String?,
    @Json(name = "timezone") val timezone: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoTimezoneResponse(
    @Json(name = "timezone") val timezone: String? = null
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

interface TimezoneApiService {
    @GET("v1/forecast")
    suspend fun getTimezoneForCoordinates(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 1
    ): OpenMeteoTimezoneResponse
}

object CuratedCities {
    val presets: List<CityOption> = listOf(
        CityOption("Москва", "Россия", "Москва", 55.7558, 37.6173, "Europe/Moscow"),
        CityOption("Санкт-Петербург", "Россия", "Санкт-Петербург", 59.9343, 30.3351, "Europe/Moscow"),
        CityOption("Казань", "Россия", "Татарстан", 55.7961, 49.1064, "Europe/Moscow"),
        CityOption("Екатеринбург", "Россия", "Свердловская обл.", 56.8389, 60.6057, "Asia/Yekaterinburg"),
        CityOption("Новосибирск", "Россия", "Новосибирская обл.", 55.0084, 82.9357, "Asia/Novosibirsk"),
        CityOption("Сочи", "Россия", "Краснодарский край", 43.5855, 39.7231, "Europe/Moscow"),
        CityOption("Владивосток", "Россия", "Приморский край", 43.1155, 131.8855, "Asia/Vladivostok"),
        CityOption("Минск", "Беларусь", "Минск", 53.9006, 27.5590, "Europe/Minsk"),
        CityOption("Алматы", "Казахстан", "Алматы", 43.2220, 76.8512, "Asia/Almaty")
    )

    fun findTimezoneForCoordinates(latitude: Double, longitude: Double): String? {
        return presets.firstOrNull {
            kotlin.math.abs(it.latitude - latitude) < 0.02 &&
                kotlin.math.abs(it.longitude - longitude) < 0.02
        }?.timezoneId
    }
}
