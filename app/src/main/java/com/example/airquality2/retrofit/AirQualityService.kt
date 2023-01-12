package com.example.airquality2.retrofit

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface AirQualityService {
    @GET("nearest_city")
    fun getAirQualityData(@Query("lat") lat: String, @Query("lon") lon: String, @Query("key") key: String): Call<AirQualityResponse>
}