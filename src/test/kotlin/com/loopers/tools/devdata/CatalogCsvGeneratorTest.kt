package com.loopers.tools.devdata

import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CatalogCsvGeneratorTest {
    @TempDir
    lateinit var outputDirectory: Path

    @Test
    fun `generates products that join to generated brands stocks and stats`() {
        CatalogCsvGenerator().generate(
            CatalogCsvGenerationConfig(
                outputDirectory = outputDirectory,
                brandCount = 3,
                productCount = 10,
                firstBrandId = 1001,
                firstProductId = 1_000_001,
                seed = 1234L,
            ),
        )

        val brands = outputDirectory.resolve("brands.csv").readLines()
        val products = outputDirectory.resolve("products.csv").readLines()
        val stocks = outputDirectory.resolve("product_stocks.csv").readLines()
        val stats = outputDirectory.resolve("product_stats.csv").readLines()

        assertEquals(3, brands.size)
        assertEquals(10, products.size)
        assertEquals(10, stocks.size)
        assertEquals(10, stats.size)

        val brandIds = brands.map { it.substringBefore(",").toLong() }.toSet()
        val productIds = products.map { it.substringBefore(",").toLong() }.toSet()
        val stockProductIds = stocks.map { it.split(",")[3].toLong() }.toSet()
        val statsProductIds = stats.map { it.split(",")[4].toLong() }.toSet()

        products.forEach { row ->
            val columns = row.split(",")
            assertTrue(columns[3].toLong() in brandIds)
        }
        assertEquals(productIds, stockProductIds)
        assertEquals(productIds, statsProductIds)
    }
}
