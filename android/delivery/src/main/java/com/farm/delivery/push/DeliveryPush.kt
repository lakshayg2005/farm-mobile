package com.farm.delivery.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.farm.core.push.PushRegistration
import com.farm.delivery.MainActivity
import com.farm.delivery.R
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

private const val CHANNEL_ID = "routes"

/** Intent extra a tapped push carries: the pool date (YYYY-MM-DD) whose route to open. */
const val EXTRA_POOL_DATE = "poolDate"

private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

/**
 * The nav route a push opens: that day's route when the data carries a valid `poolDate`, today's
 * route for any other route push (pool assigned, stop changed), else nothing.
 */
fun routeForPush(data: Map<String, String>): String? {
    val date = data[EXTRA_POOL_DATE]?.trim()?.takeIf { ISO_DATE.matches(it) }
    if (date != null) return "route?date=$date"
    // A route offer (or any route push without a date) opens the Route tab, where offers are listed
    return if (data.containsKey("poolId") || data["type"]?.startsWith("ORDER_POOLED") == true || data["type"]?.startsWith("POOL_") == true) "route" else null
}

/** True when the build carries google-services.json (Firebase then starts by itself). */
fun pushAvailable(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

/** Registers this phone's FCM token for the signed-in partner. Does nothing without Firebase. */
fun startPush(context: Context) {
    PushRegistration.start()
    if (!pushAvailable(context)) return
    FirebaseMessaging.getInstance().token.addOnSuccessListener(PushRegistration::onToken)
}

/** Receives route pushes for the delivery partner (a pool assigned, a stop removed). */
class DeliveryMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = PushRegistration.onToken(token)

    override fun onMessageReceived(message: RemoteMessage) {
        // Whatever changed (an order moved on, a route was offered), open screens re-read now.
        com.farm.core.ui.RefreshSignal.changed()
        val title = message.notification?.title ?: message.data["title"] ?: "Farm Delivery"
        val body = message.notification?.body ?: message.data["body"] ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                // High importance: a new route should sound and show at once.
                NotificationChannel(CHANNEL_ID, "Routes and stops", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            message.data.forEach { (k, v) -> putExtra(k, v) }
        }
        val pending = PendingIntent.getActivity(
            this, message.messageId?.hashCode() ?: 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_route)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        // A tag (e.g. "pool-<id>") makes "Route taken" replace the offer it is about.
        val tag = message.data["tag"]
        runCatching {
            if (tag != null) NotificationManagerCompat.from(this).notify(tag, 0, notification)
            else NotificationManagerCompat.from(this).notify(message.messageId?.hashCode() ?: body.hashCode(), notification)
        }
    }
}
