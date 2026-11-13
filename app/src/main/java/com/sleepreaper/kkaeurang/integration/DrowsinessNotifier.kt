package com.sleepreaper.kkaeurang.integration

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.detector.Level

/**
 * 졸음 감시 상단 알림.
 *
 * - 상태 알림(`drowsy_status`, LOW): 서비스가 떠 있는 동안 항상 표시. 단계가 바뀔 때마다 문구 갱신. 팝업 없음.
 * - 경고 알림(`drowsy_alert`, HIGH): WARNING 이상일 때 헤드업으로 한 번 띄우고, 그 아래로 내려가면 지운다.
 *
 * 모든 호출은 메인 스레드에서 한다.
 */
class DrowsinessNotifier(
    private val context: Context,
    private val openAppIntent: PendingIntent,
    private val stopIntent: PendingIntent,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var lastLevel: Level? = null
    private var lastScore = 0

    fun createChannels() {
        manager.createNotificationChannel(
            NotificationChannel(STATUS_CHANNEL, "졸음 감시 상태", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, "졸음 경고", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    /** 포그라운드 서비스를 시작할 때 쓰는 상태 알림 (startForeground에 넘긴다). 마지막 단계를 반영한다 */
    fun currentStatusNotification(): Notification = statusNotification(lastLevel ?: Level.NORMAL, lastScore)

    private fun statusNotification(level: Level, score: Int): Notification {
        val stopAction = Notification.Action.Builder(null, "종료", stopIntent).build()
        return Notification.Builder(context, STATUS_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("깨우랑")
            .setContentText(statusText(level, score))
            .setContentIntent(openAppIntent)
            .addAction(stopAction)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * 엔진 결과가 올 때마다 부른다. 단계가 바뀐 경우에만 알림을 갱신한다.
     * (상태 알림은 같은 id로 갱신되어 포그라운드 서비스 알림을 그대로 유지한다)
     */
    fun update(level: Level, score: Int) {
        if (level == lastLevel) return
        lastLevel = level
        lastScore = score
        manager.notify(STATUS_ID, statusNotification(level, score))
        if (level >= Level.WARNING) {
            manager.notify(ALERT_ID, alertNotification(level))
        } else {
            manager.cancel(ALERT_ID)
        }
    }

    fun cancelAlert() {
        manager.cancel(ALERT_ID)
    }

    private fun alertNotification(level: Level): Notification =
        Notification.Builder(context, ALERT_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (level == Level.DANGER) "위험" else "경고")
            .setContentText(alertText(level))
            .setContentIntent(openAppIntent)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

    private fun statusText(level: Level, score: Int) = when (level) {
        Level.NORMAL -> "졸음 감지 중"
        Level.CAUTION -> "주의 · 안전 점수 $score"
        Level.WARNING -> "경고 · 휴식이 필요해요"
        Level.DANGER -> "위험 · 졸음쉼터로 안내합니다"
    }

    private fun alertText(level: Level) =
        if (level == Level.DANGER) "졸음쉼터로 안내합니다" else "휴식이 필요해요"

    companion object {
        const val STATUS_ID = 1
        private const val ALERT_ID = 2
        private const val STATUS_CHANNEL = "drowsy_status"
        private const val ALERT_CHANNEL = "drowsy_alert"
    }
}
