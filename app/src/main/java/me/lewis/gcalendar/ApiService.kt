package me.lewis.gcalendar

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// !!! IMPORTANT: Change this to your VPS's IP address !!!
private const val BASE_URL = "http://217.154.52.175:3000/"

interface ApiService {
    @POST("login")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    @GET("events")
    fun getEvents(): Call<List<Event>>

    @POST("events")
    fun addEvent(@Body request: AddEventRequest): Call<Event>

    @DELETE("events/{id}")
    fun deleteEvent(@Path("id") eventId: Long): Call<Unit>
}

object RetrofitClient {
    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(ApiService::class.java)
    }
}