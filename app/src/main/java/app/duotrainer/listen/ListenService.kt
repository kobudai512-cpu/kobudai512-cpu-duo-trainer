package app.duotrainer.listen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import app.duotrainer.MainActivity
import app.duotrainer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 聞き流し中に動く前面サービス。画面を消しても再生が止まらないようにし、
 * 通知から 再生/一時停止・次へ・停止 を操作できるようにする。
 */
class ListenService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        val first = build(ListenController.state.value)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, first)
        }
        // 文と文の「間」で端末が眠らないように（最長 3 時間）
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "duotrainer:listen")
            .apply { acquire(3 * 60 * 60 * 1000L) }
        scope.launch {
            ListenController.state
                .map { Triple(it.index, it.playing, it.items.size) }
                .distinctUntilChanged()
                .collect {
                    val st = ListenController.state.value
                    if (!st.active) { stopSelf(); return@collect }
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(st))
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> ListenController.toggle()
            ACTION_NEXT -> ListenController.next()
            ACTION_STOP -> ListenController.stop()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun action(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, ListenService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun build(st: ListenState): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val item = st.current
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else
            @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setSmallIcon(R.drawable.ic_stat_listen)
            .setContentTitle(if (item != null) "No.${item.sentenceId}  ${item.en}" else "聞き流し")
            .setContentText("${st.title}  ${st.index + 1}/${st.items.size}" + if (st.playing) "" else "（一時停止中）")
            .setContentIntent(open)
            .setOngoing(st.playing)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, if (st.playing) "一時停止" else "再生", action(ACTION_TOGGLE, 1)).build())
            .addAction(Notification.Action.Builder(null, "次へ", action(ACTION_NEXT, 2)).build())
            .addAction(Notification.Action.Builder(null, "停止", action(ACTION_STOP, 3)).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "listen"
        private const val NOTIFICATION_ID = 41
        const val ACTION_TOGGLE = "app.duotrainer.listen.TOGGLE"
        const val ACTION_NEXT = "app.duotrainer.listen.NEXT"
        const val ACTION_STOP = "app.duotrainer.listen.STOP"

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "聞き流し", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "聞き流し中の再生コントロール"
                    setShowBadge(false)
                }
            )
        }
    }
}
