package net.hogelab.android.vpndns.domain.repository

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import net.hogelab.android.vpndns.domain.model.WhitelistEntity

/**
 * ホワイトリスト（遮断除外ルール）を管理するリポジトリ
 */
interface WhitelistRepository {
    /**
     * ホワイトリスト一覧
     */
    val whitelist: StateFlow<List<WhitelistEntity>>

    /**
     * ホワイトリストに登録する
     */
    fun addToWhitelist(hostName: String, description: String = "")

    /**
     * 説明文を更新する
     */
    fun updateDescription(hostName: String, description: String)

    /**
     * ホワイトリストから削除する
     */
    fun removeFromWhitelist(hostName: String)

    /**
     * 指定されたホストがホワイトリスト登録されているか判定する
     */
    fun isWhitelisted(hostName: String): Boolean

    /**
     * ホワイトリストをファイルに保存する
     */
    suspend fun saveWhitelist(context: Context)

    /**
     * ホワイトリストをファイルから読み込む
     */
    suspend fun loadWhitelist(context: Context)
}
