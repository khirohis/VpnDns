package net.hogelab.android.vpndns.domain.repository

import kotlinx.coroutines.flow.StateFlow
import net.hogelab.android.vpndns.domain.model.BlacklistEntry
import net.hogelab.android.vpndns.domain.model.DnsEntry
import net.hogelab.android.vpndns.domain.model.HistorySortConfig

/**
 * DNS 履歴とブラックリストを統合管理するリポジトリ
 */
interface DnsRepository {
    /**
     * DNS 履歴 (ブラックリストの状態が反映され、ソート設定に従った順序)
     */
    val history: StateFlow<List<DnsEntry>>

    /**
     * ブラックリスト一覧
     */
    val blacklist: StateFlow<List<BlacklistEntry>>

    /**
     * 現在のソート設定
     */
    val sortConfig: StateFlow<HistorySortConfig>

    /**
     * 履歴にホスト名を追加または更新する
     */
    fun addHistory(host: String)

    /**
     * 履歴をクリアする
     */
    fun clearHistory()

    /**
     * ブラックリストに登録する
     */
    fun addToBlacklist(hostName: String)

    /**
     * ブラックリストから削除する
     */
    fun removeFromBlacklist(hostName: String)

    /**
     * 指定されたホストがブロック対象かどうかを判定する
     */
    fun isBlocked(hostName: String): Boolean

    /**
     * ブラックリストをクリアする
     */
    fun clearBlacklist()

    /**
     * ソート設定を変更する
     */
    fun setSortConfig(config: HistorySortConfig)
}
