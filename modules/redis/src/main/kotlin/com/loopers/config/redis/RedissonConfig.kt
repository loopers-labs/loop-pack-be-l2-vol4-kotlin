package com.loopers.config.redis

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.redisson.config.Config
import org.redisson.config.ReadMode
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RedissonConfig(
    private val redisProperties: RedisProperties,
) {
    @Bean(destroyMethod = "shutdown")
    fun redissonClient(): RedissonClient {
        val config = Config().apply {
            setCodec(StringCodec.INSTANCE)
        }
        val masterAddress = redisProperties.master.address()
        val replicaAddresses = redisProperties.replicas
            .map { it.address() }
            .filterNot { it == masterAddress }

        if (replicaAddresses.isEmpty()) {
            config.useSingleServer()
                .setAddress(masterAddress)
                .setDatabase(redisProperties.database)
                .setTimeout(redisProperties.commandTimeout.toMillis().toInt())
        } else {
            config.useMasterSlaveServers()
                .setMasterAddress(masterAddress)
                .addSlaveAddress(*replicaAddresses.toTypedArray())
                .setReadMode(ReadMode.MASTER)
                .setDatabase(redisProperties.database)
                .setTimeout(redisProperties.commandTimeout.toMillis().toInt())
        }

        return Redisson.create(config)
    }

    private fun RedisNodeInfo.address(): String = "redis://$host:$port"
}
