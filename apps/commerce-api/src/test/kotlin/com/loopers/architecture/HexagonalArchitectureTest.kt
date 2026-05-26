package com.loopers.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import org.junit.jupiter.api.Test

/**
 * Hexagonal Architecture 의존성 방향을 ArchUnit으로 검증한다.
 *
 * 의존성 방향:
 *   interfaces → application → domain ← infrastructure
 *
 * - domain 은 어떤 다른 계층(application/infrastructure/interfaces)도 모른다.
 * - application 은 infrastructure / interfaces 를 모른다.
 * - infrastructure 는 interfaces 를 모른다.
 * - interfaces 는 infrastructure 를 직접 참조하지 않는다.
 * - domain 은 Spring / JPA 의존성을 가지지 않는다.
 */
class HexagonalArchitectureTest {

    private val importedClasses = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.loopers")

    @Test
    fun `계층 간 의존성 방향이 hexagonal 구조를 만족한다`() {
        layeredArchitecture()
            .consideringAllDependencies()
            .layer(INTERFACES).definedBy("com.loopers.interfaces..")
            .layer(APPLICATION).definedBy("com.loopers.application..")
            .layer(DOMAIN).definedBy("com.loopers.domain..")
            .layer(INFRASTRUCTURE).definedBy("com.loopers.infrastructure..")
            // domain 은 누구에게도 접근당할 수 있으나, 누구도 import 하지 않는다는 의미가 아님
            .whereLayer(INTERFACES).mayNotBeAccessedByAnyLayer()
            .whereLayer(APPLICATION).mayOnlyBeAccessedByLayers(INTERFACES)
            .whereLayer(INFRASTRUCTURE).mayNotBeAccessedByAnyLayer()
            // domain 은 모든 계층에서 참조 가능하므로 별도 제약 두지 않음
            .check(importedClasses)
    }

    @Test
    fun `domain 은 application 계층을 참조하지 않는다`() {
        noClasses()
            .that().resideInAPackage("com.loopers.domain..")
            .should().dependOnClassesThat().resideInAPackage("com.loopers.application..")
            .check(importedClasses)
    }

    @Test
    fun `domain 은 infrastructure 계층을 참조하지 않는다`() {
        noClasses()
            .that().resideInAPackage("com.loopers.domain..")
            .should().dependOnClassesThat().resideInAPackage("com.loopers.infrastructure..")
            .check(importedClasses)
    }

    @Test
    fun `domain 은 interfaces 계층을 참조하지 않는다`() {
        noClasses()
            .that().resideInAPackage("com.loopers.domain..")
            .should().dependOnClassesThat().resideInAPackage("com.loopers.interfaces..")
            .check(importedClasses)
    }

    @Test
    fun `application 은 infrastructure 계층을 참조하지 않는다`() {
        noClasses()
            .that().resideInAPackage("com.loopers.application..")
            .should().dependOnClassesThat().resideInAPackage("com.loopers.infrastructure..")
            .check(importedClasses)
    }

    @Test
    fun `application 은 interfaces 계층을 참조하지 않는다`() {
        noClasses()
            .that().resideInAPackage("com.loopers.application..")
            .should().dependOnClassesThat().resideInAPackage("com.loopers.interfaces..")
            .check(importedClasses)
    }

    @Test
    fun `infrastructure 는 interfaces 계층을 참조하지 않는다`() {
        noClasses()
            .that().resideInAPackage("com.loopers.infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("com.loopers.interfaces..")
            .check(importedClasses)
    }

    @Test
    fun `interfaces 는 infrastructure 계층을 직접 참조하지 않는다`() {
        noClasses()
            .that().resideInAPackage("com.loopers.interfaces..")
            .should().dependOnClassesThat().resideInAPackage("com.loopers.infrastructure..")
            .check(importedClasses)
    }

    @Test
    fun `domain 은 Spring framework 에 의존하지 않는다`() {
        noClasses()
            .that().resideInAPackage("com.loopers.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
            )
            .check(importedClasses)
    }

    @Test
    fun `domain 은 JPA 에 의존하지 않는다`() {
        noClasses()
            .that().resideInAPackage("com.loopers.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "jakarta.persistence..",
                "javax.persistence..",
            )
            .check(importedClasses)
    }

    @Test
    fun `RepositoryPort 인터페이스는 domain 계층에 위치한다`() {
        classes()
            .that().haveSimpleNameEndingWith("RepositoryPort")
            .should().resideInAPackage("com.loopers.domain..")
            .check(importedClasses)
    }

    @Test
    fun `RepositoryAdapter 구현체는 infrastructure 계층에 위치한다`() {
        classes()
            .that().haveSimpleNameEndingWith("RepositoryAdapter")
            .should().resideInAPackage("com.loopers.infrastructure..")
            .check(importedClasses)
    }

    @Test
    fun `Facade 구현체는 application 계층에 위치한다`() {
        classes()
            .that().haveSimpleNameEndingWith("Facade")
            .should().resideInAPackage("com.loopers.application..")
            .check(importedClasses)
    }

    @Test
    fun `Controller 는 interfaces 계층에 위치한다`() {
        classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("com.loopers.interfaces..")
            .check(importedClasses)
    }

    companion object {
        private const val INTERFACES = "interfaces"
        private const val APPLICATION = "application"
        private const val DOMAIN = "domain"
        private const val INFRASTRUCTURE = "infrastructure"
    }
}
