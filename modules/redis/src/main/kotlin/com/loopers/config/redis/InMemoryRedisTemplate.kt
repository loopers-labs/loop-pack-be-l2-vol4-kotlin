package com.loopers.config.redis

import org.springframework.data.redis.connection.RedisClusterConnection
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisSentinelConnection
import org.springframework.data.redis.connection.RedisServerCommands
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.ValueOperations
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class InMemoryRedisTemplate(
    private val store: InMemoryRedisStore = InMemoryRedisStore(),
) : RedisTemplate<String, String>() {
    private val valueOperations = valueOperationsProxy()
    private val setOperations = setOperationsProxy()

    override fun opsForValue(): ValueOperations<String, String> = valueOperations

    override fun opsForSet(): SetOperations<String, String> = setOperations

    override fun afterPropertiesSet() = Unit

    override fun delete(key: String): Boolean {
        val existed = store.remove(key)
        return existed
    }

    override fun delete(keys: MutableCollection<String>): Long =
        keys.count { store.remove(it) }.toLong()

    override fun expire(key: String, timeout: Long, unit: TimeUnit): Boolean {
        store.expire(key, Duration.ofMillis(unit.toMillis(timeout)))
        return true
    }

    override fun expire(key: String, timeout: Duration): Boolean {
        store.expire(key, timeout)
        return true
    }

    @Suppress("UNCHECKED_CAST")
    private fun valueOperationsProxy(): ValueOperations<String, String> =
        Proxy.newProxyInstance(
            ValueOperations::class.java.classLoader,
            arrayOf(ValueOperations::class.java),
        ) { _, method, args ->
            when (method.name) {
                "set" -> {
                    val key = args?.get(0) as String
                    val value = args[1] as String
                    store.setValue(key, value)
                    Unit
                }

                "get" -> store.getValue(args?.get(0) as String)
                "getOperations" -> this
                else -> unsupported(method.name)
            }
        } as ValueOperations<String, String>

    @Suppress("UNCHECKED_CAST")
    private fun setOperationsProxy(): SetOperations<String, String> =
        Proxy.newProxyInstance(
            SetOperations::class.java.classLoader,
            arrayOf(SetOperations::class.java),
        ) { _, method, args ->
            when (method.name) {
                "add" -> {
                    val key = args?.get(0) as String
                    val values = (args[1] as Array<*>).filterIsInstance<String>()
                    store.addSetMembers(key, values)
                }

                "members" -> store.getSetMembers(args?.get(0) as String)
                "getOperations" -> this
                else -> unsupported(method.name)
            }
        } as SetOperations<String, String>

    private fun unsupported(methodName: String): Nothing =
        throw UnsupportedOperationException("In-memory Redis local profile does not support $methodName.")
}

class InMemoryRedisStore {
    private val values = ConcurrentHashMap<String, String>()
    private val sets = ConcurrentHashMap<String, MutableSet<String>>()
    private val expirations = ConcurrentHashMap<String, Long>()

    fun setValue(key: String, value: String) {
        purgeIfExpired(key)
        values[key] = value
    }

    fun getValue(key: String): String? {
        purgeIfExpired(key)
        return values[key]
    }

    fun addSetMembers(key: String, members: List<String>): Long {
        purgeIfExpired(key)
        val set = sets.computeIfAbsent(key) { LinkedHashSet() }
        return members.count(set::add).toLong()
    }

    fun getSetMembers(key: String): Set<String> {
        purgeIfExpired(key)
        return sets[key]?.toSet().orEmpty()
    }

    fun expire(key: String, timeout: Duration) {
        expirations[key] = System.currentTimeMillis() + timeout.toMillis()
    }

    fun remove(key: String): Boolean {
        expirations.remove(key)
        val valueRemoved = values.remove(key) != null
        val setRemoved = sets.remove(key) != null
        return valueRemoved || setRemoved
    }

    private fun purgeIfExpired(key: String) {
        val expiresAt = expirations[key] ?: return
        if (System.currentTimeMillis() >= expiresAt) {
            remove(key)
        }
    }

    fun clear() {
        values.clear()
        sets.clear()
        expirations.clear()
    }
}

class InMemoryRedisConnectionFactory(
    private val store: InMemoryRedisStore,
) : RedisConnectionFactory {
    override fun getConnection(): RedisConnection =
        redisConnectionProxy()

    override fun getClusterConnection(): RedisClusterConnection =
        unsupportedConnectionMethod("getClusterConnection")

    override fun getSentinelConnection(): RedisSentinelConnection =
        unsupportedConnectionMethod("getSentinelConnection")

    override fun getConvertPipelineAndTxResults(): Boolean = true

    override fun translateExceptionIfPossible(ex: RuntimeException): DataAccessException? = null

    @Suppress("UNCHECKED_CAST")
    private fun redisConnectionProxy(): RedisConnection =
        Proxy.newProxyInstance(
            RedisConnection::class.java.classLoader,
            arrayOf(RedisConnection::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "serverCommands" -> redisServerCommandsProxy()
                "close" -> Unit
                "isClosed" -> false
                "isQueueing" -> false
                "isPipelined" -> false
                "getNativeConnection" -> this
                else -> unsupportedConnectionMethod(method.name)
            }
        } as RedisConnection

    @Suppress("UNCHECKED_CAST")
    private fun redisServerCommandsProxy(): RedisServerCommands =
        Proxy.newProxyInstance(
            RedisServerCommands::class.java.classLoader,
            arrayOf(RedisServerCommands::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "flushAll", "flushDb" -> {
                    store.clear()
                    Unit
                }

                else -> unsupportedConnectionMethod(method.name)
            }
        } as RedisServerCommands

    private fun unsupportedConnectionMethod(methodName: String): Nothing =
        throw UnsupportedOperationException("In-memory Redis local profile does not support $methodName.")
}
