package net.hogelab.android.vpndns.domain.repository

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import net.hogelab.android.vpndns.domain.model.BlacklistEntity

/**
 * ブラックリスト（遮断ルール）を管理するリポジトリ
 */
interface BlacklistRepository {
    /**
     * ブラックリスト一覧
     */
    val blacklist: StateFlow<List<BlacklistEntity>>

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
     * ブラックリストの保留状態を切り替える
     */
    fun togglePending(hostName: String)

    /**
     * ブラックリストをクリアする
     */
    fun clearBlacklist()

    /**
     * ブラックリストをファイルに保存する
     */
    suspend fun saveBlacklist(context: Context)

    /**
     * ブラックリストをファイルから読み込む
     */
    suspend fun loadBlacklist(context: Context)
}
