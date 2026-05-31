package net.hogelab.android.vpndns.data.repository.util

import java.util.concurrent.ConcurrentHashMap

/**
 * ドメインの後方一致検索（ワイルドカード対応）のための Trie 木
 * ラベル（.区切り）ごとに逆順で保持する。
 */
class DomainTrie {
    private class Node {
        val children = ConcurrentHashMap<String, Node>()
        var isWildcardMatch = false // ここでワイルドカード（*）による終端か
        var isPending = false
    }

    private val root = Node()

    /**
     * パターンを登録する（例: "*.google.com"）
     */
    fun insert(pattern: String, isPending: Boolean = false) {
        val labels = pattern.split(".").reversed()
        var current = root
        for (label in labels) {
            if (label == "*") {
                current.isWildcardMatch = true
                current.isPending = isPending
                return
            }
            current = current.children.getOrPut(label) { Node() }
        }
    }

    /**
     * パターンを削除する
     */
    fun remove(pattern: String) {
        val labels = pattern.split(".").reversed()
        removeRecursive(root, labels, 0)
    }

    private fun removeRecursive(node: Node, labels: List<String>, index: Int): Boolean {
        if (index == labels.size) return false // Should not happen with valid pattern

        val label = labels[index]
        if (label == "*") {
            node.isWildcardMatch = false
            return node.children.isEmpty()
        }

        val child = node.children[label] ?: return false
        val canDeleteChild = removeRecursive(child, labels, index + 1)
        
        if (canDeleteChild) {
            node.children.remove(label)
        }
        
        return !node.isWildcardMatch && node.children.isEmpty()
    }

    /**
     * 指定されたホスト名が登録済みの有効な（isPending=false）ワイルドカードパターンに合致するか判定する
     */
    fun matches(hostName: String): Boolean {
        val labels = hostName.split(".").reversed()
        var current = root
        
        // 途中で isWildcardMatch が true かつ isPending が false になればヒット
        for (label in labels) {
            if (current.isWildcardMatch && !current.isPending) return true
            current = current.children[label] ?: return false
        }
        
        return current.isWildcardMatch && !current.isPending
    }

    fun clear() {
        root.children.clear()
        root.isWildcardMatch = false
    }
}
