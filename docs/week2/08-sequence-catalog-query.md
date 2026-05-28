# Sequence 05 - 브랜드/상품 조회

## 1. Why

브랜드와 상품 조회는 전체 구매 흐름의 진입점이다.  
복잡한 쓰기 정책은 없지만, 어떤 정보를 Catalog 책임으로 보고 어떤 조합을 조회 계층에서 해결할지 분명히 해야 한다.

## 2. Diagram

```mermaid
sequenceDiagram
    autonumber
    actor User as 사용자
    participant CatalogController as CatalogController
    participant BrandAppService as BrandQueryApplicationService
    participant ProductAppService as ProductQueryApplicationService
    participant CatalogDomainService as CatalogQueryDomainService
    participant BrandRepo as BrandRepository
    participant ProductRepo as ProductRepository
    participant InventoryRepo as InventoryRepository
    participant LikeCountRepo as ProductLikeCountRepository

    alt 브랜드 상세 조회
        User->>CatalogController: GET /api/v1/brands/{brandId}
        CatalogController->>BrandAppService: getBrand(brandId)
        BrandAppService->>BrandRepo: findByIdAndIsDeletedFalse(brandId)
        BrandRepo-->>BrandAppService: brand
        BrandAppService-->>CatalogController: brand result
        CatalogController-->>User: 브랜드 응답
    else 상품 목록 조회
        User->>CatalogController: GET /api/v1/products?brandId&sort&page&size
        CatalogController->>ProductAppService: getProducts(filter, sort, page, size)
        ProductAppService->>ProductRepo: findProductsExcludingDeleted(filter, sort, page, size)
        ProductRepo-->>ProductAppService: products
        ProductAppService->>BrandRepo: findAllByIds(brandIds)
        BrandRepo-->>ProductAppService: brands
        ProductAppService->>InventoryRepo: findByProductIds(productIds)
        InventoryRepo-->>ProductAppService: inventories
        ProductAppService->>LikeCountRepo: countByProductIds(productIds)
        LikeCountRepo-->>ProductAppService: like counts
        ProductAppService->>CatalogDomainService: assembleProductSummaries(products, brands, inventories, likeCounts)
        CatalogDomainService-->>ProductAppService: list result
        ProductAppService-->>CatalogController: list result
        CatalogController-->>User: 상품 목록 응답
    else 상품 상세 조회
        User->>CatalogController: GET /api/v1/products/{productId}
        CatalogController->>ProductAppService: getProduct(productId)
        ProductAppService->>ProductRepo: findProductDetailExcludingDeleted(productId)
        ProductRepo-->>ProductAppService: product
        ProductAppService->>BrandRepo: findByIdAndIsDeletedFalse(brandId)
        BrandRepo-->>ProductAppService: brand
        ProductAppService->>InventoryRepo: findByProductId(productId)
        InventoryRepo-->>ProductAppService: inventory
        ProductAppService->>LikeCountRepo: countByProductId(productId)
        LikeCountRepo-->>ProductAppService: like count
        ProductAppService->>CatalogDomainService: assembleProductDetail(product, brand, inventory, likeCount)
        CatalogDomainService-->>ProductAppService: detail result
        ProductAppService-->>CatalogController: detail result
        CatalogController-->>User: 상품 상세 응답
    end
```

## 3. Key Points

- Controller 는 각 Application Service 로 진입하고, Application Service 가 repository interface 를 통해 필요한 도메인 데이터를 조회한다.
- 브랜드와 상품은 같은 Catalog 문맥 안에 있지만, 상품 목록/상세는 브랜드 정보, 재고 정보, 좋아요 수가 함께 조합된 읽기 모델이 된다.
- 도메인 간 조합 규칙은 상태 없는 `CatalogQueryDomainService`가 담당하고, Application Service 는 조회 흐름 orchestration 에 집중한다.
- 좋아요 여부, 좋아요 수 같은 부가 정보는 조회 모델에서 조합할 수 있으며, 필요 시 캐시/프로젝션으로 최적화할 수 있다.
- soft delete 된 브랜드와 상품은 고객 조회에서 기본적으로 제외돼야 한다.
