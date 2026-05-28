package net.hogelab.android.vpndns.domain.repository

import kotlinx.coroutines.flow.StateFlow
import net.hogelab.android.vpndns.domain.model.DnsEntry
import net.hogelab.android.vpndns.domain.model.HistorySortConfig

/**
 * リクエストのあった DNS ホスト名の履歴を管理するリポジトリ
 */
interface DnsHistoryRepository {
    /**
     * ホスト名の履歴（ソート設定に従った順序）
     */
    val history: StateFlow<List<DnsEntry>>

    /**
     * 現在のソート設定
     */
    val sortConfig: StateFlow<HistorySortConfig>

    /**
     * ホスト名を追加または更新する
     */
    fun addHost(host: String)

    /**
     * ソート設定を変更する
     */
    fun setSortConfig(config: HistorySortConfig)

    /**
     * 履歴をクリアする
     */
    fun clear()
}
