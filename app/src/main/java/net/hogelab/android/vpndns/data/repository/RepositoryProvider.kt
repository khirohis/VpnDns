package net.hogelab.android.vpndns.data.repository

import net.hogelab.android.vpndns.domain.repository.DnsRepository

/**
 * リポジトリのインスタンスを提供するシングルトン
 * (将来的に Hilt 等に移行するまでの暫定的な管理)
 */
object RepositoryProvider {
    val dnsRepository: DnsRepository by lazy {
        InMemoryDnsRepository()
    }
}
