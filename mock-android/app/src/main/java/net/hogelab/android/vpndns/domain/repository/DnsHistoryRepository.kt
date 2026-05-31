package net.hogelab.android.vpndns.domain.repository

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import net.hogelab.android.vpndns.domain.model.DnsHistoryEntity
import net.hogelab.android.vpndns.domain.model.HistorySortConfig

/**
 * DNS 履歴とブラックリストを統合管理するリポジトリ
 */
interface DnsHistoryRepository {
    /**
     * DNS 履歴 (ソート設定に従った順序)
     */
    val history: StateFlow<List<DnsHistoryEntity>>

    /**
     * 現在のソート設定
     */
    val sortConfig: StateFlow<HistorySortConfig>

    /**
     * DNS リクエストイベント (Host 名)
     */
    val dnsRequestEvents: SharedFlow<String>

    /**
     * DNS リクエストが発生したことを通知する
     */
    suspend fun notifyDnsRequest(hostName: String)

    /**
     * 履歴にホスト名を追加または更新する
     */
    suspend fun addHistory(host: String)

    /**
     * 履歴をクリアする
     */
    fun clearHistory()

    /**
     * ソート設定を変更する
     */
    fun setSortConfig(config: HistorySortConfig)
}
