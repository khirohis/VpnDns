package net.hogelab.android.vpndns.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.hogelab.android.vpndns.MainActivity
import net.hogelab.android.vpndns.data.dns.KotlinDnsInterceptor
import net.hogelab.android.vpndns.data.repository.RepositoryProvider
import net.hogelab.android.vpndns.domain.interceptor.DnsInterceptor
import net.hogelab.android.vpndns.domain.repository.DnsRepository

/**
 * VPN インターフェースの確立とサービスライフサイクルの管理を行うクラス
 * 実際のパケット処理は DnsInterceptor に委譲する
 */
class VpnDnsService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val dnsRepository: DnsRepository = RepositoryProvider.dnsRepository
    private lateinit var dnsInterceptor: DnsInterceptor

    companion object {
        private const val TAG = "VpnDnsService"
        private const val CHANNEL_ID = "vpn_service_channel"
        private const val NOTIFICATION_ID = 1

        private val _connectionState = MutableStateFlow(false)
        val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        dnsInterceptor = KotlinDnsInterceptor(dnsRepository) { socket ->
            protect(socket)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (_connectionState.value) return

        // 前処理としてブラックリストをロード
        dnsRepository.loadBlacklist(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        try {
            vpnInterface = Builder()
                .setSession("VpnDns")
                .addAddress("10.0.0.2", 32)
                // IPv4 DNS Servers (Google DNS pairs)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .addRoute("8.8.8.8", 32)
                .addRoute("8.8.4.4", 32)
                // IPv6 DNS Servers (Google DNS pairs)
                .addDnsServer("2001:4860:4860::8888")
                .addDnsServer("2001:4860:4860::8844")
                .addRoute("2001:4860:4860::8888", 128)
                .addRoute("2001:4860:4860::8844", 128)
                .establish()

            if (vpnInterface != null) {
                _connectionState.value = true
                Log.d(TAG, "VPN established: Delegating packet processing to Interceptor")
                
                // パケットインターセプト処理を開始
                dnsInterceptor.start(vpnInterface!!)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN", e)
            stopVpn()
        }
    }

    private fun stopVpn() {
        try {
            _connectionState.value = false
            
            // 処理を停止
            dnsInterceptor.stop()

            // VPN 停止時にブラックリストを保存し、履歴をクリア
            dnsRepository.saveBlacklist(this)
            dnsRepository.clearHistory()

            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "VPN stopped")
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VpnDns")
            .setContentText("DNS protection is active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
