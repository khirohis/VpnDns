package net.hogelab.android.vpndns.domain.interceptor

import android.os.ParcelFileDescriptor
import java.net.InetAddress

/**
 * DNS クエリをインターセプトして処理するインターフェース
 */
interface DnsInterceptor {
    /**
     * DNS クエリを処理し、応答パケット（DNS ペイロード）を返す。
     */
    suspend fun handleDnsQuery(
        query: ByteArray,
        serverAddr: InetAddress,
        isIPv6: Boolean
    ): ByteArray?

    /**
     * パケット処理を開始する
     *
     * @param pfd TUN インターフェースの ParcelFileDescriptor
     */
    fun start(pfd: ParcelFileDescriptor)

    /**
     * パケット処理を停止する
     */
    fun stop()
}
