package me.lewis.gcalendar

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // This function is called when a notification is received while the app is in the foreground.
        // Firebase handles showing the notification automatically when the app is in the background or closed.
        super.onMessageReceived(remoteMessage)
    }

    override fun onNewToken(token: String) {
        // This is called when a new notification token is generated for the device.
        // Our login logic already handles sending the latest token, so we don't need to do much here.
        super.onNewToken(token)
    }
}