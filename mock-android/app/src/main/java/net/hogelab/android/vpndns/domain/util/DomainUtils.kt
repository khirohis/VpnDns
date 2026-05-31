package net.hogelab.android.vpndns.domain.util

object DomainUtils {
    /**
     * ホスト名からベースドメイン（セカンドレベルドメイン）を抽出する
     * 例: 
     *   "www.google.com" -> "google.com"
     *   "a.b.c.example.co.jp" -> "example.co.jp" (co.jp などの例外対応を含む)
     *   "localhost" -> "localhost"
     */
    fun extractBaseDomain(hostName: String): String {
        val cleanHost = hostName.removePrefix("*.").lowercase()
        val parts = cleanHost.split(".")
        if (parts.size <= 2) return cleanHost

        // co.jp, ne.jp, com.br などの 2パーツ構成の TLD を簡易判定
        // 本来は Public Suffix List を使うべきだが、要件に合わせてセカンドレベルまでを基本とする
        val last2 = "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
        val commonTwoPartTlds = setOf(
            "co.jp", "ne.jp", "ac.jp", "ad.jp", "ed.jp", "go.jp", "gr.jp", "lg.jp", "or.jp",
            "co.uk", "org.uk", "me.uk", "com.br", "org.br", "net.au", "com.au"
        )

        return if (commonTwoPartTlds.contains(last2) && parts.size >= 3) {
            "${parts[parts.size - 3]}.$last2"
        } else {
            last2
        }
    }
}
