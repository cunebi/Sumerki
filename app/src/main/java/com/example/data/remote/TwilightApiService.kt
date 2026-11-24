package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class SunriseSunsetResponse(
    @Json(name = "status") val status: String,
    @Json(name = "results") val results: SunriseSunsetResults?
)

@JsonClass(generateAdapter = true)
data class SunriseSunsetResults(
    @Json(name = "sunrise") val sunrise: String?,
    @Json(name = "sunset") val sunset: String?,
    @Json(name = "civil_twilight_begin") val civilTwilightBegin: String?,
    @Json(name = "nautical_twilight_begin") val nauticalTwilightBegin: String?,
    @Json(name = "astronomical_twilight_begin") val astronomicalTwilightBegin: String?
)

interface TwilightApiService {
    @GET("json")
    suspend fun getTwilightForDate(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("date") dateIso: String,
        @Query("formatted") formatted: Int = 0,
        @Query("tzid") tzid: String? = null
    ): SunriseSunsetResponse
}
