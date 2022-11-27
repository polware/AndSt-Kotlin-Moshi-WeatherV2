package com.polware.weatherv2.interfaces

import com.polware.weatherv2.models.WeatherResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherAPI {

    @GET("weather")
    suspend fun getWeatherByLocation(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") app_id: String?,
        @Query("units") units: String?,
        @Query("lang") lang: String?
    ): Response<WeatherResponse>

    @GET("weather")
    suspend fun getWeatherByCity(
        @Query("q") city: String?,
        @Query("appid") app_id: String?,
        @Query("units") units: String?,
        @Query("lang") lang: String?
    ): Response<WeatherResponse>

}