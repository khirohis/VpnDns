package net.hogelab.android.vpndns.domain.repository

import kotlinx.coroutines.flow.StateFlow
import net.hogelab.android.vpndns.domain.model.BlacklistEntry

/**
 * ブロック対象のドメインを管理するリポジトリ
 */
interface BlacklistRepository {
    /**
     * ブロックリストの全エントリ
     */
    val entries: StateFlow<List<BlacklistEntry>>

    /**
     * 指定されたホストがブロック対象かどうかを高速に判定する
     */
    fun isBlocked(hostName: String): Boolean

    /**
     * ブロック対象に追加する
     */
    fun add(hostName: String)

    /**
     * ブロック対象から削除する
     */
    fun remove(hostName: String)

    /**
     * リストをクリアする
     */
    fun clear()
}
