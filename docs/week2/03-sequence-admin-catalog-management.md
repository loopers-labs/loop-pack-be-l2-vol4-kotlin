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
    participant BrandService as BrandService (application)
    participant ProductService as ProductService (application)
    participant InventoryService as InventoryService (application)
    participant CatalogManagementService as CatalogManagementService (domain)
    participant BrandRepo as BrandRepository
    participant ProductRepo as ProductRepository
    participant InventoryRepo as InventoryRepository

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
        AdminCatalogFacade->>BrandService: getBrand(brandId)
        BrandService->>BrandRepo: findById(brandId)
        BrandRepo-->>BrandService: brand
        BrandService-->>AdminCatalogFacade: brand
        AdminCatalogFacade->>ProductService: getProductsByBrandId(brandId)
        ProductService->>ProductRepo: findAllByBrandId(brandId)
        ProductRepo-->>ProductService: products
        ProductService-->>AdminCatalogFacade: products
        AdminCatalogFacade->>CatalogManagementService: deleteBrand(brand, products)
        CatalogManagementService-->>AdminCatalogFacade: deleted brand and products
        AdminCatalogFacade->>BrandService: save(brand)
        BrandService->>BrandRepo: save(brand)
        AdminCatalogFacade->>ProductService: saveAll(products)
        ProductService->>ProductRepo: saveAll(products)
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
            Note over AdminCatalogFacade,InventoryRepo: 하나의 Catalog 관리 트랜잭션
            AdminCatalogFacade->>CatalogManagementService: createProduct(brand, command)
            CatalogManagementService-->>AdminCatalogFacade: product and inventory
            AdminCatalogFacade->>ProductService: save(product)
            ProductService->>ProductRepo: save(product)
            ProductRepo-->>ProductService: saved product
            ProductService-->>AdminCatalogFacade: saved product
            AdminCatalogFacade->>InventoryService: save(inventory with saved product.id)
            InventoryService->>InventoryRepo: save(inventory)
            InventoryRepo-->>InventoryService: saved inventory
            InventoryService-->>AdminCatalogFacade: saved inventory
            AdminCatalogFacade-->>AdminCatalogController: success
            AdminCatalogController-->>Admin: 성공 응답
        end
    else 상품 수정
        Admin->>AdminCatalogController: PUT /api-admin/v1/products/{productId}
        AdminCatalogController->>AdminCatalogFacade: updateProduct(command)
        AdminCatalogFacade->>ProductService: getProduct(productId)
        ProductService->>ProductRepo: findById(productId)
        ProductRepo-->>ProductService: product
        ProductService-->>AdminCatalogFacade: product
        AdminCatalogFacade->>CatalogManagementService: updateProduct(product, command)
        CatalogManagementService-->>AdminCatalogFacade: updated product
        AdminCatalogFacade->>ProductService: save(updated product)
        ProductService->>ProductRepo: save(updated product)
        ProductRepo-->>ProductService: saved product
        ProductService-->>AdminCatalogFacade: saved product
        AdminCatalogFacade-->>AdminCatalogController: success
        AdminCatalogController-->>Admin: 성공 응답
    end
```

## 3. Key Points

- `AdminCatalogFacade`가 관리자 Catalog 유스케이스 흐름을 조정하고, 도메인별 service(application)가 repository interface 를 통해 데이터를 조회하거나 저장한다.
- 브랜드 삭제, 상품 생성, 상품 수정처럼 여러 도메인 객체가 함께 움직이는 규칙은 의존성이 없는 `CatalogManagementService`(domain)가 담당한다.
- 브랜드 삭제는 연관 상품 soft delete 정책을 동반하므로 Facade 단위 트랜잭션 경계를 함께 고려해야 한다.
- 상품 등록은 브랜드 존재 검증이 먼저고, 상품과 `Inventory`를 함께 만들어야 한다. 상품 수정의 `brandId` 변경 금지 정책은 `CatalogManagementService`(domain)에서 다루는 편이 자연스럽다.
