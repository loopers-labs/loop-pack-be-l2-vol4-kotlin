package com.loopers.tools.devdata

import net.datafaker.Faker
import net.datafaker.service.RandomService
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Random
import kotlin.io.path.createDirectories

private const val DEFAULT_BRAND_COUNT = 1_000
private const val DEFAULT_PRODUCT_COUNT = 1_000_000
private const val DEFAULT_FIRST_BRAND_ID = 1_001L
private const val DEFAULT_FIRST_PRODUCT_ID = 1_000_001L
private const val DEFAULT_SEED = 20_260_619L
private const val DEFAULT_CREATED_AT = "2026-06-19 00:00:00.000000"

private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")

data class CatalogCsvGenerationConfig(
    val outputDirectory: Path,
    val brandCount: Int = DEFAULT_BRAND_COUNT,
    val productCount: Int = DEFAULT_PRODUCT_COUNT,
    val firstBrandId: Long = DEFAULT_FIRST_BRAND_ID,
    val firstProductId: Long = DEFAULT_FIRST_PRODUCT_ID,
    val seed: Long = DEFAULT_SEED,
    val createdAt: String = DEFAULT_CREATED_AT,
)

class CatalogCsvGenerator {
    fun generate(config: CatalogCsvGenerationConfig) {
        require(config.brandCount > 0) { "brandCount must be greater than 0." }
        require(config.productCount > 0) { "productCount must be greater than 0." }

        config.outputDirectory.createDirectories()

        val faker = Faker(Locale.ENGLISH, RandomService(Random(config.seed)))
        val random = Random(config.seed)
        val baseTime = LocalDateTime.parse(config.createdAt, timestampFormatter)

        writeBrands(config, faker, baseTime)
        writeProducts(config, faker, random, baseTime)
        writeProductStocks(config, random, baseTime)
        writeProductStats(config, random, baseTime)
    }

    private fun writeBrands(
        config: CatalogCsvGenerationConfig,
        faker: Faker,
        baseTime: LocalDateTime,
    ) {
        csvWriter(config.outputDirectory.resolve("brands.csv")).use { writer ->
            repeat(config.brandCount) { index ->
                val id = config.firstBrandId + index
                val now = timestamp(baseTime, index)
                writer.writeCsvLine(
                    id,
                    now,
                    now,
                    safeText(faker.company().name(), 100, "Bulk Brand $id"),
                    "ACTIVE",
                )
            }
        }
    }

    private fun writeProducts(
        config: CatalogCsvGenerationConfig,
        faker: Faker,
        random: Random,
        baseTime: LocalDateTime,
    ) {
        csvWriter(config.outputDirectory.resolve("products.csv")).use { writer ->
            repeat(config.productCount) { index ->
                val id = config.firstProductId + index
                val brandId = config.firstBrandId + (index % config.brandCount)
                val now = timestamp(baseTime, index)
                writer.writeCsvLine(
                    id,
                    now,
                    now,
                    brandId,
                    safeText(faker.commerce().productName(), 150, "Bulk Product $id"),
                    ((random.nextInt(2_000) + 1) * 100L),
                    "ON_SALE",
                )
            }
        }
    }

    private fun writeProductStocks(
        config: CatalogCsvGenerationConfig,
        random: Random,
        baseTime: LocalDateTime,
    ) {
        csvWriter(config.outputDirectory.resolve("product_stocks.csv")).use { writer ->
            repeat(config.productCount) { index ->
                val productId = config.firstProductId + index
                val stockQuantity = random.nextInt(500) + 1
                val reservedQuantity = random.nextInt(minOf(stockQuantity, 20) + 1)
                val now = timestamp(baseTime, index)
                writer.writeCsvLine(
                    productId,
                    now,
                    now,
                    productId,
                    reservedQuantity,
                    stockQuantity,
                )
            }
        }
    }

    private fun writeProductStats(
        config: CatalogCsvGenerationConfig,
        random: Random,
        baseTime: LocalDateTime,
    ) {
        csvWriter(config.outputDirectory.resolve("product_stats.csv")).use { writer ->
            repeat(config.productCount) { index ->
                val productId = config.firstProductId + index
                val now = timestamp(baseTime, index)
                writer.writeCsvLine(
                    productId,
                    now,
                    now,
                    random.nextInt(10_000).toLong(),
                    productId,
                )
            }
        }
    }

    private fun timestamp(baseTime: LocalDateTime, offsetSeconds: Int): String =
        baseTime.plusSeconds(offsetSeconds.toLong()).format(timestampFormatter)

    private fun csvWriter(path: Path): BufferedWriter =
        Files.newBufferedWriter(path, StandardCharsets.UTF_8)

    private fun BufferedWriter.writeCsvLine(vararg values: Any) {
        write(values.joinToString(",") { it.csvValue() })
        newLine()
    }

    private fun Any.csvValue(): String =
        when (this) {
            is Number -> toString()
            else -> "\"${toString().replace("\"", "\"\"")}\""
        }

    private fun safeText(value: String, maxLength: Int, fallback: String): String {
        val normalized = value.replace('\r', ' ').replace('\n', ' ').trim()
        return (normalized.ifBlank { fallback }).take(maxLength)
    }
}

fun main(args: Array<String>) {
    val options = parseOptions(args)
    val outputDirectory = options["output"]?.let(Path::of) ?: Path.of("docker/mysql/generated")

    CatalogCsvGenerator().generate(
        CatalogCsvGenerationConfig(
            outputDirectory = outputDirectory,
            brandCount = options["brand-count"]?.toInt() ?: DEFAULT_BRAND_COUNT,
            productCount = options["product-count"]?.toInt() ?: DEFAULT_PRODUCT_COUNT,
            firstBrandId = options["first-brand-id"]?.toLong() ?: DEFAULT_FIRST_BRAND_ID,
            firstProductId = options["first-product-id"]?.toLong() ?: DEFAULT_FIRST_PRODUCT_ID,
            seed = options["seed"]?.toLong() ?: DEFAULT_SEED,
        ),
    )
}

private fun parseOptions(args: Array<String>): Map<String, String> {
    val options = mutableMapOf<String, String>()
    var index = 0
    while (index < args.size) {
        val key = args[index]
        require(key.startsWith("--")) { "Expected option name, got $key." }
        require(index + 1 < args.size) { "Missing value for $key." }
        options[key.removePrefix("--")] = args[index + 1]
        index += 2
    }
    return options
}
