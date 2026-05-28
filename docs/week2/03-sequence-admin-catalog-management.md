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
    participant AdminCatalogAppService as AdminCatalogApplicationService
    participant CatalogDomainService as CatalogManagementDomainService
    participant BrandRepo as BrandRepository
    participant ProductRepo as ProductRepository
    participant InventoryRepo as InventoryRepository

    alt 브랜드 등록/수정
        Admin->>AdminCatalogController: POST or PUT /api-admin/v1/brands
        AdminCatalogController->>AdminCatalogAppService: saveBrand(command)
        AdminCatalogAppService->>BrandRepo: save(brand)
        BrandRepo-->>AdminCatalogAppService: saved brand
        AdminCatalogAppService-->>AdminCatalogController: success
        AdminCatalogController-->>Admin: 성공 응답
    else 브랜드 삭제
        Admin->>AdminCatalogController: DELETE /api-admin/v1/brands/{brandId}
        AdminCatalogController->>AdminCatalogAppService: deleteBrand(brandId)
        Note over AdminCatalogAppService,ProductRepo: 하나의 Catalog 관리 트랜잭션
        AdminCatalogAppService->>BrandRepo: findById(brandId)
        BrandRepo-->>AdminCatalogAppService: brand
        AdminCatalogAppService->>ProductRepo: findAllByBrandId(brandId)
        ProductRepo-->>AdminCatalogAppService: products
        AdminCatalogAppService->>CatalogDomainService: deleteBrand(brand, products)
        CatalogDomainService-->>AdminCatalogAppService: deleted brand and products
        AdminCatalogAppService->>BrandRepo: save(brand)
        AdminCatalogAppService->>ProductRepo: saveAll(products)
        AdminCatalogAppService-->>AdminCatalogController: success
        AdminCatalogController-->>Admin: 성공 응답
    else 상품 등록
        Admin->>AdminCatalogController: POST /api-admin/v1/products
        AdminCatalogController->>AdminCatalogAppService: createProduct(command)
        AdminCatalogAppService->>BrandRepo: findById(command.brandId)
        BrandRepo-->>AdminCatalogAppService: brand or null
        alt 브랜드 없음
            AdminCatalogAppService-->>AdminCatalogController: 등록 실패
            AdminCatalogController-->>Admin: 실패 응답
        else 브랜드 존재
            Note over AdminCatalogAppService,InventoryRepo: 하나의 Catalog 관리 트랜잭션
            AdminCatalogAppService->>CatalogDomainService: createProduct(brand, command)
            CatalogDomainService-->>AdminCatalogAppService: product and inventory
            AdminCatalogAppService->>ProductRepo: save(product)
            ProductRepo-->>AdminCatalogAppService: saved product
            AdminCatalogAppService->>InventoryRepo: save(inventory with saved product.id)
            InventoryRepo-->>AdminCatalogAppService: saved inventory
            AdminCatalogAppService-->>AdminCatalogController: success
            AdminCatalogController-->>Admin: 성공 응답
        end
    else 상품 수정
        Admin->>AdminCatalogController: PUT /api-admin/v1/products/{productId}
        AdminCatalogController->>AdminCatalogAppService: updateProduct(command)
        AdminCatalogAppService->>ProductRepo: findById(productId)
        ProductRepo-->>AdminCatalogAppService: product
        AdminCatalogAppService->>CatalogDomainService: updateProduct(product, command)
        CatalogDomainService-->>AdminCatalogAppService: updated product
        AdminCatalogAppService->>ProductRepo: save(updated product)
        ProductRepo-->>AdminCatalogAppService: saved product
        AdminCatalogAppService-->>AdminCatalogController: success
        AdminCatalogController-->>Admin: 성공 응답
    end
```

## 3. Key Points

- `AdminCatalogApplicationService`가 repository interface 를 통해 필요한 도메인 데이터를 조회하고 관리자 유스케이스 흐름을 조정한다.
- 브랜드 삭제, 상품 생성, 상품 수정처럼 여러 도메인 객체가 함께 움직이는 규칙은 `CatalogManagementDomainService`가 담당한다.
- 브랜드 삭제는 연관 상품 soft delete 정책을 동반하므로 Application Service 단위 트랜잭션 경계를 함께 고려해야 한다.
- 상품 등록은 브랜드 존재 검증이 먼저고, 상품과 `Inventory`를 함께 만들어야 한다. 상품 수정의 `brandId` 변경 금지 정책은 `CatalogManagementDomainService`에서 다루는 편이 자연스럽다.
