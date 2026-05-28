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
    participant CatalogFacade as CatalogFacade
    participant BrandService as BrandService (application)
    participant ProductService as ProductService (application)
    participant InventoryService as InventoryService (application)
    participant ProductLikeService as ProductLikeService (application)
    participant CatalogQueryService as CatalogQueryService (domain)
    participant BrandRepo as BrandRepository
    participant ProductRepo as ProductRepository
    participant InventoryRepo as InventoryRepository
    participant LikeCountRepo as ProductLikeCountRepository

    alt 브랜드 상세 조회
        User->>CatalogController: GET /api/v1/brands/{brandId}
        CatalogController->>CatalogFacade: getBrand(brandId)
        CatalogFacade->>BrandService: getBrand(brandId)
        BrandService->>BrandRepo: findByIdAndIsDeletedFalse(brandId)
        BrandRepo-->>BrandService: brand
        BrandService-->>CatalogFacade: brand result
        CatalogFacade-->>CatalogController: brand result
        CatalogController-->>User: 브랜드 응답
    else 상품 목록 조회
        User->>CatalogController: GET /api/v1/products?brandId&sort&page&size
        CatalogController->>CatalogFacade: getProducts(filter, sort, page, size)
        CatalogFacade->>ProductService: getProducts(filter, sort, page, size)
        ProductService->>ProductRepo: findProductsExcludingDeleted(filter, sort, page, size)
        ProductRepo-->>ProductService: products
        ProductService-->>CatalogFacade: products
        CatalogFacade->>BrandService: getBrands(brandIds)
        BrandService->>BrandRepo: findAllByIds(brandIds)
        BrandRepo-->>BrandService: brands
        BrandService-->>CatalogFacade: brands
        CatalogFacade->>InventoryService: getInventories(productIds)
        InventoryService->>InventoryRepo: findByProductIds(productIds)
        InventoryRepo-->>InventoryService: inventories
        InventoryService-->>CatalogFacade: inventories
        CatalogFacade->>ProductLikeService: getLikeCounts(productIds)
        ProductLikeService->>LikeCountRepo: countByProductIds(productIds)
        LikeCountRepo-->>ProductLikeService: like counts
        ProductLikeService-->>CatalogFacade: like counts
        CatalogFacade->>CatalogQueryService: assembleProductSummaries(products, brands, inventories, likeCounts)
        CatalogQueryService-->>CatalogFacade: list result
        CatalogFacade-->>CatalogController: list result
        CatalogController-->>User: 상품 목록 응답
    else 상품 상세 조회
        User->>CatalogController: GET /api/v1/products/{productId}
        CatalogController->>CatalogFacade: getProduct(productId)
        CatalogFacade->>ProductService: getProduct(productId)
        ProductService->>ProductRepo: findProductDetailExcludingDeleted(productId)
        ProductRepo-->>ProductService: product
        ProductService-->>CatalogFacade: product
        CatalogFacade->>BrandService: getBrand(brandId)
        BrandService->>BrandRepo: findByIdAndIsDeletedFalse(brandId)
        BrandRepo-->>BrandService: brand
        BrandService-->>CatalogFacade: brand
        CatalogFacade->>InventoryService: getInventory(productId)
        InventoryService->>InventoryRepo: findByProductId(productId)
        InventoryRepo-->>InventoryService: inventory
        InventoryService-->>CatalogFacade: inventory
        CatalogFacade->>ProductLikeService: getLikeCount(productId)
        ProductLikeService->>LikeCountRepo: countByProductId(productId)
        LikeCountRepo-->>ProductLikeService: like count
        ProductLikeService-->>CatalogFacade: like count
        CatalogFacade->>CatalogQueryService: assembleProductDetail(product, brand, inventory, likeCount)
        CatalogQueryService-->>CatalogFacade: detail result
        CatalogFacade-->>CatalogController: detail result
        CatalogController-->>User: 상품 상세 응답
    end
```

## 3. Key Points

- Controller 는 Facade 로 진입하고, Facade 는 도메인별 service(application)를 호출해 유스케이스 흐름을 조정한다.
- 도메인별 service(application)는 repository interface 를 통해 자기 도메인의 데이터를 조회한다.
- 브랜드와 상품은 같은 Catalog 문맥 안에 있지만, 상품 목록/상세는 브랜드 정보, 재고 정보, 좋아요 수가 함께 조합된 읽기 모델이 된다.
- 도메인 간 조합 규칙은 상태와 의존성이 없는 `CatalogQueryService`(domain)가 담당하고, Facade 는 더 큰 흐름의 orchestration 에 집중한다.
- 좋아요 여부, 좋아요 수 같은 부가 정보는 조회 모델에서 조합할 수 있으며, 필요 시 캐시/프로젝션으로 최적화할 수 있다.
- soft delete 된 브랜드와 상품은 고객 조회에서 기본적으로 제외돼야 한다.
