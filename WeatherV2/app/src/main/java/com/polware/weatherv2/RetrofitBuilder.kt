package com.polware.weatherv2

import com.polware.weatherv2.Constants.BASE_URL
import com.polware.weatherv2.interfaces.WeatherAPI
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

object RetrofitBuilder {
    val retrofitService : WeatherAPI by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        // Create Retrofit client
        return@lazy retrofit.create(WeatherAPI::class.java)
    }
}