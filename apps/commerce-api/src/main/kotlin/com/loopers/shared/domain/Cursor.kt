package com.loopers.shared.domain

/**
 * keyset 페이지네이션 커서의 base.
 *
 * 정렬마다 키 구성이 달라 단일 타입에 묶을 수 없으므로, 정렬별 구현체가 이 타입을 구현한다.
 * (latest=[IdCursor], price_asc=`PriceCursor(price,id)` 등). sealed 대신 interface 로 둬
 * 추후 도메인 모듈 분리 시에도 다른 모듈에서 구현체를 추가할 수 있게 한다.
 */
interface Cursor
