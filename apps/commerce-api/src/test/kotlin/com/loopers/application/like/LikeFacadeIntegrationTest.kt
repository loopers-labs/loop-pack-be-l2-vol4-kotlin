package com.loopers.application.like

import com.loopers.application.product.CreateProductCommand
import com.loopers.application.product.ProductFacade
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.common.PageRequest
import com.loopers.domain.like.LikeService
import com.loopers.domain.product.LikeCountQueryPort
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepositoryPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class LikeFacadeIntegrationTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val productFacade: ProductFacade,
    private val brandRepositoryPort: BrandRepositoryPort,
    private val userRepositoryPort: UserRepositoryPort,
    private val likeService: LikeService,
    private val likeCountQueryPort: LikeCountQueryPort,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun saveUser(name: String = "tester"): User =
        userRepositoryPort.save(User.create(name = name, birth = LocalDate.of(2000, 1, 1), email = "$name@loopers.com"))

    private fun saveProduct(brandName: String = "Nike", productName: String = "p"): Long {
        val brand = brandRepositoryPort.save(Brand.create(name = brandName, description = "x"))
        return productFacade.createProduct(
            CreateProductCommand(name = productName, price = 100L, description = "d", brandId = brand.id, quantity = 10),
        ).id
    }

    @DisplayName("좋아요 등록(like) 통합 흐름")
    @Nested
    inner class LikeRegister {
        @DisplayName("정상 등록 시 LikeCount가 1 증가한다.")
        @Test
        fun increasesLikeCount() {
            val user = saveUser()
            val productId = saveProduct()

            likeFacade.like(user.id, productId)

            assertThat(likeCountQueryPort.countByProductId(productId)).isEqualTo(1L)
        }

        @DisplayName("동일 회원-상품에 좋아요를 중복 등록해도 멱등하게 LikeCount는 1만 유지된다.")
        @Test
        fun idempotentOnDuplicate() {
            val user = saveUser()
            val productId = saveProduct()

            likeFacade.like(user.id, productId)
            likeFacade.like(user.id, productId)
            likeFacade.like(user.id, productId)

            assertThat(likeCountQueryPort.countByProductId(productId)).isEqualTo(1L)
        }

        @DisplayName("존재하지 않는 상품에 좋아요를 등록하면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenProductMissing() {
            val user = saveUser()

            val result = assertThrows<CoreException> {
                likeFacade.like(user.id, 9999L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("좋아요 취소(unlike) 통합 흐름")
    @Nested
    inner class LikeCancel {
        @DisplayName("좋아요가 등록된 상태에서 취소하면 LikeCount가 1 감소한다.")
        @Test
        fun decreasesLikeCount() {
            val user = saveUser()
            val productId = saveProduct()
            likeFacade.like(user.id, productId)

            likeFacade.unlike(user.id, productId)

            assertThat(likeCountQueryPort.countByProductId(productId)).isEqualTo(0L)
        }

        @DisplayName("좋아요가 없는 상태에서 취소해도 멱등하게 동작하며 LikeCount는 0 이하로 떨어지지 않는다.")
        @Test
        fun idempotentOnMissing() {
            val user = saveUser()
            val productId = saveProduct()

            likeFacade.unlike(user.id, productId)
            likeFacade.unlike(user.id, productId)

            assertThat(likeCountQueryPort.countByProductId(productId)).isEqualTo(0L)
        }

        @DisplayName("존재하지 않는 상품에 좋아요 취소를 요청하면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenProductMissing() {
            val user = saveUser()

            val result = assertThrows<CoreException> {
                likeFacade.unlike(user.id, 9999L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("좋아요한 상품 목록 조회(getLikedProducts) 통합 흐름")
    @Nested
    inner class GetLikedProducts {
        @DisplayName("본인이 자신의 좋아요한 상품 목록을 조회하면 product id/name/price/brandName이 담긴 페이지를 반환한다.")
        @Test
        fun returnsLikedProductSummaries() {
            val user = saveUser()
            val brand = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val productIds = (0 until 3).map {
                productFacade.createProduct(
                    CreateProductCommand(name = "p$it", price = (100L + it), description = "d", brandId = brand.id, quantity = 1),
                ).id
            }
            productIds.forEach { likeFacade.like(user.id, it) }

            val result = likeFacade.getLikedProducts(
                targetUserId = user.id,
                requesterUserId = user.id,
                pageRequest = PageRequest(page = 0, size = 10),
            )

            assertThat(result.totalElements).isEqualTo(3L)
            assertThat(result.items).hasSize(3)
            assertThat(result.items.map { it.productId }).containsExactlyInAnyOrderElementsOf(productIds)
            assertThat(result.items.map { it.brandName }).containsOnly("Nike")
        }

        @DisplayName("좋아요한 상품이 없으면 빈 페이지를 반환한다.")
        @Test
        fun returnsEmptyPage() {
            val user = saveUser()

            val result = likeFacade.getLikedProducts(
                targetUserId = user.id,
                requesterUserId = user.id,
                pageRequest = PageRequest(page = 0, size = 10),
            )

            assertThat(result.items).isEmpty()
            assertThat(result.totalElements).isEqualTo(0L)
        }

        @DisplayName("존재하지 않는 userId로 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenUserMissing() {
            val result = assertThrows<CoreException> {
                likeFacade.getLikedProducts(
                    targetUserId = 9999L,
                    requesterUserId = 9999L,
                    pageRequest = PageRequest(page = 0, size = 10),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("타인의 좋아요 목록을 조회하면 FORBIDDEN 예외가 발생한다.")
        @Test
        fun throwsForbidden_whenAccessingOtherUser() {
            val owner = saveUser(name = "owner")
            val stranger = saveUser(name = "stranger")

            val result = assertThrows<CoreException> {
                likeFacade.getLikedProducts(
                    targetUserId = owner.id,
                    requesterUserId = stranger.id,
                    pageRequest = PageRequest(page = 0, size = 10),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.FORBIDDEN)
        }
    }

    @DisplayName("등록 → 취소 → 재등록 시 LikeCount는 1로 정상 동작한다.")
    @Test
    fun likeCountRoundTrip() {
        val user = saveUser()
        val productId = saveProduct()

        likeFacade.like(user.id, productId)
        assertThat(likeCountQueryPort.countByProductId(productId)).isEqualTo(1L)

        likeFacade.unlike(user.id, productId)
        assertThat(likeCountQueryPort.countByProductId(productId)).isEqualTo(0L)

        likeFacade.like(user.id, productId)
        assertThat(likeCountQueryPort.countByProductId(productId)).isEqualTo(1L)

        // unused helper avoid warning
        assertThat(likeService.findAllProductIdsByUserId(user.id)).contains(productId)
    }
}
