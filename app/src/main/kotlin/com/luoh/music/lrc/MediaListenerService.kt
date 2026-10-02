package com.luoh.music.lrc

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // 不需要处理通知内容，只需要监听服务存在即可
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
