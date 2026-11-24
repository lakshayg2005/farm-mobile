package com.farm.customer.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.farm.core.push.PushRegistration
import com.farm.customer.MainActivity
import com.farm.customer.R
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

private const val CHANNEL_ID = "orders"

/** Intent extras a notification tap opens (read by MainActivity). */
const val EXTRA_ORDER_ID = "orderId"
const val EXTRA_RESERVATION_ID = "reservationId"

/** Where a push with this data payload should open; the same keys as the in-app inbox payload. */
fun pushTarget(data: Map<String, String>): NotificationTarget? {
    data[EXTRA_ORDER_ID]?.trim()?.ifEmpty { null }?.let { return NotificationTarget.Order(it) }
    data[EXTRA_RESERVATION_ID]?.trim()?.ifEmpty { null }?.let { return NotificationTarget.Reservation(it) }
    return null
}

/** The nav route for a notification target: an order's detail or the reservation. */
fun routeFor(target: NotificationTarget): String = when (target) {
    is NotificationTarget.Order -> "order/${target.orderId}"
    is NotificationTarget.Reservation -> "reservation/${target.reservationId}"
}

/** True when the build carries the team's google-services.json (Firebase starts by itself then). */
fun pushAvailable(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

/** Fetches the current FCM token and hands it to [PushRegistration]. Does nothing without Firebase. */
fun startPush(context: Context) {
    PushRegistration.start()
    if (!pushAvailable(context)) return
    FirebaseMessaging.getInstance().token.addOnSuccessListener(PushRegistration::onToken)
}

/** Receives FCM messages and token changes for the customer app. */
class FarmMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = PushRegistration.onToken(token)

    override fun onMessageReceived(message: RemoteMessage) {
        // Whatever changed (an order moved on, a route was offered), open screens re-read now.
        com.farm.core.ui.RefreshSignal.changed()
        val title = message.notification?.title ?: message.data["title"] ?: "Farm"
        val body = message.notification?.body ?: message.data["body"] ?: return
        val target = pushTarget(message.data)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Orders and reservations", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            when (target) {
                is NotificationTarget.Order -> putExtra(EXTRA_ORDER_ID, target.orderId)
                is NotificationTarget.Reservation -> putExtra(EXTRA_RESERVATION_ID, target.reservationId)
                null -> Unit
            }
        }
        val pending = PendingIntent.getActivity(
            this, message.messageId?.hashCode() ?: 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(message.messageId?.hashCode() ?: body.hashCode(), notification) }
    }
}
