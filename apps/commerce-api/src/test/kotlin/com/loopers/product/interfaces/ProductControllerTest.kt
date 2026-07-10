package com.loopers.product.interfaces

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.brand.application.BrandCreateCommand
import com.loopers.brand.application.BrandService
import com.loopers.product.application.ProductCreateCommand
import com.loopers.product.application.ProductService
import com.loopers.support.DatabaseCleanup
import com.loopers.utils.RedisCleanUp
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val brandService: BrandService,
    private val productService: ProductService,
    private val databaseCleanup: DatabaseCleanup,
    private val redisCleanUp: RedisCleanUp,
) {
    @BeforeEach
    fun cleanup() {
        databaseCleanup.execute()
        redisCleanUp.truncateAll()
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    inner class GetProducts {
        @DisplayName("첫 페이지를 좋아요순으로 조회하면 성공 응답과 목록을 반환한다.")
        @Test
        fun returnsFirstPage() {
            seedProducts(12)

            mockMvc.perform(get(PRODUCTS_PATH).param(PARAM_SORT, LIKES_DESC).param(PARAM_SIZE, "5"))
                .andExpect(status().isOk)
                .andExpect(jsonPath(JSON_IS_SUCCESS).value(true))
                .andExpect(jsonPath("$.data.content.length()").value(5))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.nextCursor").isNotEmpty)
        }

        @DisplayName("nextCursor 로 다음 페이지를 조회하면 이전 페이지와 겹치지 않는다.")
        @Test
        fun cursorRoundTripDoesNotOverlap() {
            seedProducts(12)

            val first = mockMvc.perform(get(PRODUCTS_PATH).param(PARAM_SORT, LIKES_DESC).param(PARAM_SIZE, "5"))
                .andExpect(status().isOk)
                .andReturn()
            val firstJson = objectMapper.readTree(first.response.contentAsString).path(DATA)
            val firstIds = firstJson.path(CONTENT).map { it.path(ID).asLong() }.toSet()
            val nextCursor = firstJson.path(NEXT_CURSOR).asText()

            val second = mockMvc.perform(
                get(PRODUCTS_PATH).param(PARAM_SORT, LIKES_DESC).param(PARAM_SIZE, "5").param(PARAM_CURSOR, nextCursor),
            )
                .andExpect(status().isOk)
                .andReturn()
            val secondIds = objectMapper.readTree(second.response.contentAsString)
                .path(DATA).path(CONTENT).map { it.path(ID).asLong() }.toSet()

            assert(firstIds.intersect(secondIds).isEmpty()) { "페이지가 겹치면 안 된다: $firstIds vs $secondIds" }
        }

        @DisplayName("size 가 1~100 범위를 벗어나면 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenSizeOutOfRange() {
            mockMvc.perform(get(PRODUCTS_PATH).param(PARAM_SIZE, "0"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath(JSON_CODE).value(CODE_INVALID_PAGE_SIZE))

            mockMvc.perform(get(PRODUCTS_PATH).param(PARAM_SIZE, "101"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath(JSON_CODE).value(CODE_INVALID_PAGE_SIZE))
        }

        @DisplayName("잘못된 커서 문자열이면 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenCursorIsInvalid() {
            mockMvc.perform(get(PRODUCTS_PATH).param(PARAM_CURSOR, "!!!not-base64!!!"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath(JSON_CODE).value(CODE_INVALID_CURSOR))
        }
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    inner class GetProduct {
        @DisplayName("존재하는 상품이면 상세 정보를 반환한다.")
        @Test
        fun returnsDetail_whenProductExists() {
            val brandId = brandService.register(BrandCreateCommand(name = "brand-detail")).id
            val productId = productService.register(
                ProductCreateCommand(brandId = brandId, name = "product-detail", price = 9_900, stock = 100),
            ).id

            mockMvc.perform(get("$PRODUCTS_PATH/$productId"))
                .andExpect(status().isOk)
                .andExpect(jsonPath(JSON_IS_SUCCESS).value(true))
                .andExpect(jsonPath("$.data.id").value(productId))
                .andExpect(jsonPath("$.data.brandId").value(brandId))
                .andExpect(jsonPath("$.data.brandName").value("brand-detail"))
                .andExpect(jsonPath("$.data.name").value("product-detail"))
        }

        @DisplayName("존재하지 않는 상품이면 404 NOT_FOUND 를 반환한다.")
        @Test
        fun returnsNotFound_whenProductMissing() {
            mockMvc.perform(get("$PRODUCTS_PATH/999999"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath(JSON_CODE).value(CODE_PRODUCT_NOT_FOUND))
        }
    }

    private fun seedProducts(count: Int) {
        val brandId = brandService.register(BrandCreateCommand(name = "brand-list")).id
        repeat(count) { i ->
            productService.register(
                ProductCreateCommand(brandId = brandId, name = "product-$i", price = (i + 1) * 1_000L, stock = 100),
            )
        }
    }

    private companion object {
        private const val PRODUCTS_PATH = "/api/v1/products"

        private const val PARAM_SORT = "sort"
        private const val PARAM_SIZE = "size"
        private const val PARAM_CURSOR = "cursor"
        private const val LIKES_DESC = "LIKES_DESC"

        private const val DATA = "data"
        private const val CONTENT = "content"
        private const val ID = "id"
        private const val NEXT_CURSOR = "nextCursor"

        private const val JSON_IS_SUCCESS = "$.isSuccess"
        private const val JSON_CODE = "$.code"
        private const val CODE_INVALID_PAGE_SIZE = "PRODUCT:INVALID_PAGE_SIZE"
        private const val CODE_INVALID_CURSOR = "PRODUCT:INVALID_PRODUCT_CURSOR"
        private const val CODE_PRODUCT_NOT_FOUND = "PRODUCT:PRODUCT_NOT_FOUND"
    }
}
