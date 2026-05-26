package com.loopers.interfaces.api

import com.loopers.application.like.LikeFacade
import com.loopers.application.product.CreateProductCommand
import com.loopers.application.product.ProductFacade
import com.loopers.application.user.SignupCommand
import com.loopers.application.user.UserFacade
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.product.ProductRepositoryPort
import com.loopers.domain.stock.StockRepositoryPort
import com.loopers.utils.DatabaseCleanUp
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductAdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandRepositoryPort: BrandRepositoryPort,
    private val productFacade: ProductFacade,
    private val productRepositoryPort: ProductRepositoryPort,
    private val stockRepositoryPort: StockRepositoryPort,
    private val userFacade: UserFacade,
    private val likeFacade: LikeFacade,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ADMIN_ENDPOINT = "/api-admin/v1/products"
        private const val ADMIN_LDAP = "loopers.admin"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun headers(ldap: String? = ADMIN_LDAP, contentType: MediaType? = MediaType.APPLICATION_JSON): HttpHeaders =
        HttpHeaders().apply {
            contentType?.let { setContentType(it) }
            ldap?.let { set("X-Loopers-Ldap", it) }
        }

    private fun adminCreate(body: Map<String, Any>, ldap: String? = ADMIN_LDAP) =
        testRestTemplate.exchange(
            ADMIN_ENDPOINT,
            HttpMethod.POST,
            HttpEntity(body, headers(ldap = ldap)),
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )

    private fun adminGet(id: Long, ldap: String? = ADMIN_LDAP) =
        testRestTemplate.exchange(
            "$ADMIN_ENDPOINT/$id",
            HttpMethod.GET,
            HttpEntity<Any>(headers(ldap = ldap, contentType = null)),
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )

    private fun adminUpdate(id: Long, body: Map<String, Any>, ldap: String? = ADMIN_LDAP) =
        testRestTemplate.exchange(
            "$ADMIN_ENDPOINT/$id",
            HttpMethod.PUT,
            HttpEntity(body, headers(ldap = ldap)),
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )

    private fun adminDelete(id: Long, ldap: String? = ADMIN_LDAP) =
        testRestTemplate.exchange(
            "$ADMIN_ENDPOINT/$id",
            HttpMethod.DELETE,
            HttpEntity<Any>(headers(ldap = ldap, contentType = null)),
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )

    private fun adminList(brandId: Long? = null, page: Int? = null, size: Int? = null, ldap: String? = ADMIN_LDAP) =
        run {
            val params = buildList {
                brandId?.let { add("brandId=$it") }
                page?.let { add("page=$it") }
                size?.let { add("size=$it") }
            }
            val url = if (params.isEmpty()) ADMIN_ENDPOINT else "$ADMIN_ENDPOINT?${params.joinToString("&")}"
            testRestTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity<Any>(headers(ldap = ldap, contentType = null)),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )
        }

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    inner class Create {

        @DisplayName("어드민이 유효한 정보를 보내면, 상품과 재고가 함께 생성된다.")
        @Test
        fun createsProductAndStock() {
            val brand = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val body = mapOf(
                "name" to "에어맥스",
                "price" to 100000,
                "description" to "운동화",
                "brandId" to brand.id,
                "quantity" to 50,
            )

            val response = adminCreate(body)

            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.get("name")).isEqualTo("에어맥스") },
                { assertThat(data?.get("stockQuantity")).isEqualTo(50) },
            )
            val createdId = (data?.get("id") as Number).toLong()
            assertThat(stockRepositoryPort.findByProductId(createdId)?.quantity).isEqualTo(50)
        }

        @DisplayName("존재하지 않는 brandId로 등록하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenBrandMissing() {
            val body = mapOf(
                "name" to "p",
                "price" to 100,
                "description" to "d",
                "brandId" to 9999L,
                "quantity" to 10,
            )

            val response = adminCreate(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("어드민 헤더가 없으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderMissing() {
            val body = mapOf("name" to "p", "price" to 100, "description" to "d", "brandId" to 1L, "quantity" to 1)
            val response = adminCreate(body, ldap = null)
            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @DisplayName("GET /api-admin/v1/products/{id}")
    @Nested
    inner class GetDetail {
        @DisplayName("어드민이 상품 상세를 조회하면, brandName/stockQuantity/likeCount를 반환한다.")
        @Test
        fun returnsDetail() {
            val brand = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val detail = productFacade.createProduct(
                CreateProductCommand(name = "p", price = 100L, description = "d", brandId = brand.id, quantity = 10),
            )

            val response = adminGet(detail.id)

            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.get("brandName")).isEqualTo("Nike") },
                { assertThat(data?.get("stockQuantity")).isEqualTo(10) },
            )
        }

        @DisplayName("존재하지 않는 id로 조회하면 404 NOT_FOUND.")
        @Test
        fun returnsNotFound_whenMissing() {
            val response = adminGet(9999L)
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{id}")
    @Nested
    inner class Update {
        @DisplayName("brandId가 동일하면 상품과 재고가 함께 갱신된다.")
        @Test
        fun updatesProductAndStock() {
            val brand = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val detail = productFacade.createProduct(
                CreateProductCommand(name = "old", price = 100L, description = "d", brandId = brand.id, quantity = 10),
            )
            val body = mapOf(
                "name" to "new",
                "price" to 200,
                "description" to "newD",
                "brandId" to brand.id,
                "quantity" to 99,
            )

            val response = adminUpdate(detail.id, body)

            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.get("name")).isEqualTo("new") },
                { assertThat(data?.get("stockQuantity")).isEqualTo(99) },
            )
        }

        @DisplayName("brandId를 다른 값으로 변경 시도하면 400 BAD_REQUEST.")
        @Test
        fun returnsBadRequest_whenBrandIdChanged() {
            val brand1 = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val brand2 = brandRepositoryPort.save(Brand.create(name = "Adidas", description = "y"))
            val detail = productFacade.createProduct(
                CreateProductCommand(name = "p", price = 100L, description = "d", brandId = brand1.id, quantity = 10),
            )
            val body = mapOf(
                "name" to "p",
                "price" to 100,
                "description" to "d",
                "brandId" to brand2.id,
                "quantity" to 10,
            )

            val response = adminUpdate(detail.id, body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("존재하지 않는 상품 수정은 404 NOT_FOUND.")
        @Test
        fun returnsNotFound_whenMissing() {
            val body = mapOf("name" to "x", "price" to 100, "description" to "d", "brandId" to 1L, "quantity" to 1)
            val response = adminUpdate(9999L, body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{id}")
    @Nested
    inner class Delete {
        @DisplayName("어드민이 상품을 삭제하면, 상품과 재고가 함께 soft delete된다.")
        @Test
        fun deletesProductAndStock() {
            val brand = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val detail = productFacade.createProduct(
                CreateProductCommand(name = "p", price = 100L, description = "d", brandId = brand.id, quantity = 10),
            )

            val response = adminDelete(detail.id)

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(productRepositoryPort.findByIdOrNull(detail.id)).isNull()
            assertThat(stockRepositoryPort.findByProductId(detail.id)).isNull()
        }

        @DisplayName("존재하지 않는 id로 삭제는 404 NOT_FOUND.")
        @Test
        fun returnsNotFound_whenMissing() {
            val response = adminDelete(9999L)
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    inner class ListProducts {
        @DisplayName("brandId 필터 없이 조회하면 전체 상품을 반환한다.")
        @Test
        fun returnsAllProducts() {
            val brand1 = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val brand2 = brandRepositoryPort.save(Brand.create(name = "Adidas", description = "y"))
            productFacade.createProduct(CreateProductCommand(name = "n", price = 100L, description = "d", brandId = brand1.id, quantity = 1))
            productFacade.createProduct(CreateProductCommand(name = "a", price = 100L, description = "d", brandId = brand2.id, quantity = 1))

            val response = adminList()

            val data = response.body?.data as? Map<*, *>
            val items = data?.get("items") as? List<*>
            assertThat(items?.size).isEqualTo(2)
        }

        @DisplayName("brandId 필터로 조회하면 해당 브랜드 상품만 반환한다.")
        @Test
        fun returnsFilteredProducts() {
            val brand1 = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val brand2 = brandRepositoryPort.save(Brand.create(name = "Adidas", description = "y"))
            repeat(2) { productFacade.createProduct(CreateProductCommand(name = "n$it", price = 100L, description = "d", brandId = brand1.id, quantity = 1)) }
            productFacade.createProduct(CreateProductCommand(name = "a", price = 100L, description = "d", brandId = brand2.id, quantity = 1))

            val response = adminList(brandId = brand1.id)

            val data = response.body?.data as? Map<*, *>
            val items = data?.get("items") as? List<*>
            assertThat(items?.size).isEqualTo(2)
        }

        @DisplayName("상품이 없으면 빈 items와 totalElements 0을 반환한다.")
        @Test
        fun returnsEmpty_whenNoProducts() {
            val response = adminList()
            val data = response.body?.data as? Map<*, *>
            val items = data?.get("items") as? List<*>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(items?.size).isEqualTo(0) },
                { assertThat((data?.get("totalElements") as? Number)?.toLong()).isEqualTo(0L) },
            )
        }

        @DisplayName("어드민 헤더 없으면 403 FORBIDDEN.")
        @Test
        fun returnsForbidden_whenNoLdap() {
            val response = adminList(ldap = null)
            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @DisplayName("어드민이 상품 목록을 조회하면, 각 상품의 좋아요 수(likeCount)도 응답에 포함된다.")
        @Test
        fun includesLikeCountPerProduct() {
            val brand = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val popularProductId = productFacade.createProduct(
                CreateProductCommand(name = "popular", price = 100L, description = "d", brandId = brand.id, quantity = 1),
            ).id
            val quietProductId = productFacade.createProduct(
                CreateProductCommand(name = "quiet", price = 100L, description = "d", brandId = brand.id, quantity = 1),
            ).id
            val user1 = userFacade.signup(
                SignupCommand(
                    loginId = "u1",
                    rawPassword = "password1234",
                    name = "u1",
                    birth = LocalDate.of(2000, 1, 1),
                    email = "u1@example.com",
                ),
            ).id
            val user2 = userFacade.signup(
                SignupCommand(
                    loginId = "u2",
                    rawPassword = "password1234",
                    name = "u2",
                    birth = LocalDate.of(2000, 1, 1),
                    email = "u2@example.com",
                ),
            ).id
            likeFacade.like(user1, popularProductId)
            likeFacade.like(user2, popularProductId)

            val response = adminList()

            val data = response.body?.data as? Map<*, *>
            val items = data?.get("items") as? List<*>
            val byProductId = items?.associate { (it as Map<*, *>)["id"] to (it["likeCount"] as Number).toLong() }
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(byProductId?.get(popularProductId.toInt())).isEqualTo(2L) },
                { assertThat(byProductId?.get(quietProductId.toInt())).isEqualTo(0L) },
            )
        }
    }
}
