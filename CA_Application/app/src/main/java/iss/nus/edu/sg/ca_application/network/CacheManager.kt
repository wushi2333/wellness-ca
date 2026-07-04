// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.network

/**
 * Simple in-memory LRU cache with TTL-based expiration.
 * Used to reduce duplicate API calls during navigation.
 */
object CacheManager {

    private const val MAX_ENTRIES = 50
    private const val DEFAULT_TTL_MS = 60_000L // 1 minute

    private data class CacheEntry<T>(val data: T, val timestamp: Long = System.currentTimeMillis()) {
        fun isExpired(ttlMs: Long = DEFAULT_TTL_MS): Boolean =
            System.currentTimeMillis() - timestamp > ttlMs
    }

    private val cache = object : LinkedHashMap<String, CacheEntry<Any>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry<Any>>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        val entry = cache[key] ?: return null
        if (entry.isExpired()) {
            cache.remove(key)
            return null
        }
        return entry.data as? T
    }

    fun <T> put(key: String, data: T) {
        @Suppress("UNCHECKED_CAST")
        cache[key] = CacheEntry(data as Any)
    }

    fun invalidate(keyPrefix: String) {
        cache.keys.removeAll { it.startsWith(keyPrefix) }
    }

    fun clear() {
        cache.clear()
    }
}
