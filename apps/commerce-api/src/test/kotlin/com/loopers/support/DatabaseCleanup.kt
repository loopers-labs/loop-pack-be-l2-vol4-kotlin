package com.loopers.support

import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.Table
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Profile("test")
class DatabaseCleanup(
    private val em: EntityManager,
) : InitializingBean {
    private lateinit var tables: List<TableInfo>

    override fun afterPropertiesSet() {
        tables = em.metamodel.entities
            .filter { it.javaType.isAnnotationPresent(Entity::class.java) }
            .map { entityType ->
                val tableAnnotation = entityType.javaType.getAnnotation(Table::class.java)
                val tableName = tableAnnotation?.name?.takeIf(String::isNotBlank)
                    ?: entityType.name.toSnakeCase()
                val idJavaType = entityType.idType.javaType.kotlin.javaObjectType
                TableInfo(tableName, hasNumericId = Number::class.java.isAssignableFrom(idJavaType))
            }
    }

    @Transactional
    fun execute() {
        em.flush()
        em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate()
        tables.forEach { (tableName, hasNumericId) ->
            em.createNativeQuery("TRUNCATE TABLE $tableName").executeUpdate()
            if (hasNumericId) {
                em.createNativeQuery("ALTER TABLE $tableName AUTO_INCREMENT = 1").executeUpdate()
            }
        }
        em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate()
    }

    private data class TableInfo(val tableName: String, val hasNumericId: Boolean)

    private fun String.toSnakeCase(): String =
        replace(CAMEL_CASE_BOUNDARY, "$1_$2").lowercase()

    private companion object {
        private val CAMEL_CASE_BOUNDARY = Regex("([a-z0-9])([A-Z])")
    }
}
