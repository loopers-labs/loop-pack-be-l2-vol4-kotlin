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
    participant BrandService as BrandService
    participant ProductService as ProductService
    participant BrandRepo as BrandRepository
    participant ProductRepo as ProductRepository

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
        ProductRepo-->>ProductService: product summaries
        ProductService-->>CatalogFacade: list result
        CatalogFacade-->>CatalogController: list result
        CatalogController-->>User: 상품 목록 응답
    else 상품 상세 조회
        User->>CatalogController: GET /api/v1/products/{productId}
        CatalogController->>CatalogFacade: getProduct(productId)
        CatalogFacade->>ProductService: getProduct(productId)
        ProductService->>ProductRepo: findProductDetailExcludingDeleted(productId)
        ProductRepo-->>ProductService: product detail
        ProductService-->>CatalogFacade: detail result
        CatalogFacade-->>CatalogController: detail result
        CatalogController-->>User: 상품 상세 응답
    end
```

## 3. Key Points

- `CatalogFacade`가 API 응답 조합 흐름을 맡고, `BrandService`와 `ProductService`는 각 도메인 repository를 다룬다.
- 브랜드와 상품은 같은 Catalog 문맥 안에 있지만, 상품 목록/상세는 브랜드 정보가 함께 조합된 읽기 모델이 된다.
- 좋아요 여부, 좋아요 수 같은 부가 정보는 조회 모델에서 조합할 수 있으며, 필요 시 캐시/프로젝션으로 최적화할 수 있다.
- soft delete 된 브랜드와 상품은 고객 조회에서 기본적으로 제외돼야 한다.
