package com.loopers.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * Hexagonal Architecture 의존성 방향을 ArchUnit으로 검증한다.
 *
 * 의존성 방향:
 *   interfaces → application → domain ← infrastructure
 *
 * 단, ApplicationServicePort 는 interfaces 계층에 위치하며 application 의 Adapter가
 * 이를 구현하기 때문에 application → interfaces 방향 의존이 *Port 한정으로* 허용된다.
 *
 * - domain 은 어떤 다른 계층(application/infrastructure/interfaces)도 모른다.
 * - application 은 infrastructure 를 모른다. interfaces 는 ApplicationServicePort 한정으로만 참조한다.
 * - infrastructure 는 interfaces 를 모른다.
 * - interfaces 는 infrastructure 를 직접 참조하지 않는다.
 * - domain 은 Spring / JPA 의존성을 가지지 않는다.
 */
class HexagonalArchitectureTest {

    private val importedClasses = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.loopers")

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
    fun `application 은 interfaces 계층을 ApplicationServicePort 외에는 참조하지 않는다`() {
        val interfacesNonPort: DescribedPredicate<JavaClass> = object : DescribedPredicate<JavaClass>(
            "reside in com.loopers.interfaces.. and have simple name not ending with ApplicationServicePort",
        ) {
            override fun test(input: JavaClass): Boolean =
                input.packageName.startsWith("com.loopers.interfaces") &&
                    !input.simpleName.endsWith("ApplicationServicePort")
        }
        noClasses()
            .that().resideInAPackage("com.loopers.application..")
            .should().dependOnClassesThat(interfacesNonPort)
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
    fun `ApplicationServicePort 인터페이스는 interfaces 계층에 위치한다`() {
        classes()
            .that().haveSimpleNameEndingWith("ApplicationServicePort")
            .should().resideInAPackage("com.loopers.interfaces..")
            .check(importedClasses)
    }

    @Test
    fun `ApplicationServiceAdapter 구현체는 application 계층에 위치한다`() {
        classes()
            .that().haveSimpleNameEndingWith("ApplicationServiceAdapter")
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
}
