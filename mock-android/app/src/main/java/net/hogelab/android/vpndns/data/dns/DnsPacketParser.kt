package net.hogelab.android.vpndns.data.dns

import android.util.Log

/**
 * DNS パケットの解析を行うクラス
 */
object DnsPacketParser {
    private const val TAG = "DnsPacketParser"

    /**
     * DNS ペイロードからクエリ情報を解析する
     *
     * @param dnsPayload UDP ペイロード (DNS パケット全体)
     * @return 解析されたクエリ情報。失敗した場合は null
     */
    fun parseQuery(dnsPayload: ByteArray): DnsQuery? {
        try {
            // DNS ヘッダーは 12 バイト固定
            if (dnsPayload.size < 12) return null

            val transactionId = ((dnsPayload[0].toInt() and 0xFF shl 8) or (dnsPayload[1].toInt() and 0xFF)).toShort()
            val flags = ((dnsPayload[2].toInt() and 0xFF shl 8) or (dnsPayload[3].toInt() and 0xFF)).toShort()
            val qdCount = ((dnsPayload[4].toInt() and 0xFF shl 8) or (dnsPayload[5].toInt() and 0xFF))

            // 質問セクションがない場合はパース不可
            if (qdCount == 0) return null

            val sb = StringBuilder()
            var pos = 12 
            val questionStart = 12
            
            // QNAME (ホスト名) の解析
            while (pos < dnsPayload.size) {
                val len = dnsPayload[pos].toInt() and 0xFF
                if (len == 0) {
                    pos++ // 終端の 0x00 をスキップ
                    break
                }
                
                if (pos + 1 + len > dnsPayload.size) return null
                
                if (sb.isNotEmpty()) sb.append(".")
                val label = String(dnsPayload, pos + 1, len, Charsets.US_ASCII)
                sb.append(label)
                
                pos += (1 + len)
            }

            // QTYPE (2 bytes) + QCLASS (2 bytes) の存在チェック
            if (pos + 4 > dnsPayload.size) return null

            val qType = ((dnsPayload[pos].toInt() and 0xFF shl 8) or (dnsPayload[pos + 1].toInt() and 0xFF))
            val qClass = ((dnsPayload[pos + 2].toInt() and 0xFF shl 8) or (dnsPayload[pos + 3].toInt() and 0xFF))
            pos += 4

            // 質問セクション全体のコピー
            val rawQuestionSection = dnsPayload.copyOfRange(questionStart, pos)

            val hostName = sb.toString()
            return if (hostName.isEmpty()) null else DnsQuery(
                transactionId = transactionId,
                flags = flags,
                hostName = hostName,
                qType = qType,
                qClass = qClass,
                rawQuestionSection = rawQuestionSection
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse DNS Query", e)
            return null
        }
    }

    /**
     * 後方互換性のためのホスト名のみの解析
     */
    fun parseHostName(dnsPayload: ByteArray): String? {
        return parseQuery(dnsPayload)?.hostName
    }
}
