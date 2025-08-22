package me.lewis.gcalendar

data class LoginRequest(val name: String, val pin: String, val fcmToken: String)
data class LoginResponse(val success: Boolean, val message: String)
data class Event(val id: Long, val user: String, val date: String, val time: String)
data class AddEventRequest(val user: String, val date: String, val time: String)