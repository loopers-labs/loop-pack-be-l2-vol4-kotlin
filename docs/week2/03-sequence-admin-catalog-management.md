# Sequence 03 - 관리자 브랜드/상품 관리

## 1. Why

관리자 Catalog 흐름은 같은 `Brand`, `Product` 도메인을 사용하지만 고객 조회와는 책임이 다르다.  
운영자가 어떤 규칙으로 브랜드와 상품을 생성, 수정, soft delete 하는지 드러내야 고객용 조회 모델과 쓰기 모델이 섞이지 않는다.

## 2. Diagram

```mermaid
sequenceDiagram
    autonumber
    actor Admin as 관리자
    participant AdminCatalogController as AdminCatalogController
    participant AdminCatalogFacade as AdminCatalogFacade
    participant BrandService as BrandService
    participant ProductService as ProductService
    participant BrandRepo as BrandRepository
    participant ProductRepo as ProductRepository

    alt 브랜드 등록/수정
        Admin->>AdminCatalogController: POST or PUT /api-admin/v1/brands
        AdminCatalogController->>AdminCatalogFacade: saveBrand(command)
        AdminCatalogFacade->>BrandService: saveBrand(command)
        BrandService->>BrandRepo: save(brand)
        BrandRepo-->>BrandService: saved brand
        BrandService-->>AdminCatalogFacade: success
        AdminCatalogFacade-->>AdminCatalogController: success
        AdminCatalogController-->>Admin: 성공 응답
    else 브랜드 삭제
        Admin->>AdminCatalogController: DELETE /api-admin/v1/brands/{brandId}
        AdminCatalogController->>AdminCatalogFacade: deleteBrand(brandId)
        Note over AdminCatalogFacade,ProductRepo: 하나의 Catalog 관리 트랜잭션
        AdminCatalogFacade->>ProductService: markDeletedByBrandId(brandId)
        ProductService->>ProductRepo: markDeletedByBrandId(brandId)
        ProductRepo-->>ProductService: products marked deleted
        ProductService-->>AdminCatalogFacade: success
        AdminCatalogFacade->>BrandService: markDeleted(brandId)
        BrandService->>BrandRepo: markDeleted(brandId)
        BrandRepo-->>BrandService: brand marked deleted
        BrandService-->>AdminCatalogFacade: success
        AdminCatalogFacade-->>AdminCatalogController: success
        AdminCatalogController-->>Admin: 성공 응답
    else 상품 등록
        Admin->>AdminCatalogController: POST /api-admin/v1/products
        AdminCatalogController->>AdminCatalogFacade: createProduct(command)
        AdminCatalogFacade->>BrandService: getBrand(command.brandId)
        BrandService->>BrandRepo: findById(command.brandId)
        BrandRepo-->>BrandService: brand or null
        alt 브랜드 없음
            BrandService-->>AdminCatalogFacade: 등록 실패
            AdminCatalogFacade-->>AdminCatalogController: 등록 실패
            AdminCatalogController-->>Admin: 실패 응답
        else 브랜드 존재
            BrandService-->>AdminCatalogFacade: brand
            AdminCatalogFacade->>ProductService: createProduct(command)
            ProductService->>ProductRepo: save(product)
            ProductRepo-->>ProductService: saved product
            ProductService-->>AdminCatalogFacade: success
            AdminCatalogFacade-->>AdminCatalogController: success
            AdminCatalogController-->>Admin: 성공 응답
        end
    else 상품 수정
        Admin->>AdminCatalogController: PUT /api-admin/v1/products/{productId}
        AdminCatalogController->>AdminCatalogFacade: updateProduct(command)
        AdminCatalogFacade->>ProductService: updateProduct(command)
        ProductService->>ProductRepo: findById(productId)
        ProductRepo-->>ProductService: product
        ProductService->>ProductService: brandId 변경 요청 차단
        ProductService->>ProductRepo: save(updated product)
        ProductRepo-->>ProductService: saved product
        ProductService-->>AdminCatalogFacade: success
        AdminCatalogFacade-->>AdminCatalogController: success
        AdminCatalogController-->>Admin: 성공 응답
    end
```

## 3. Key Points

- `AdminCatalogFacade`가 관리자 Catalog 흐름을 조정하고, `BrandService`와 `ProductService`가 각자 repository를 다룬다.
- 브랜드 삭제는 연관 상품 soft delete 정책을 동반하므로 Facade 단위 트랜잭션 경계를 함께 고려해야 한다.
- 상품 등록은 브랜드 존재 검증이 먼저고, 상품 수정은 `brandId` 변경 금지 정책을 `ProductService`에서 지켜야 한다.
