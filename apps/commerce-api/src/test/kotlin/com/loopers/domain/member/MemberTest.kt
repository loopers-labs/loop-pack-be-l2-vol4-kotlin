package com.loopers.domain.member

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertAll
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MemberTest {

    @DisplayName("요구사항을 모두 충족하면 정상적으로 생성된다.")
    @Test
    fun createsMember_whenRequiredFieldsAreProvided() {
        val loginId = "loopers123"
        val password = "encodedPassword"
        val name = "gunyoung"
        val birthDate = LocalDate.of(1970, 1, 1)
        val email = "loopers@gmail.com"

        val member = Member(
            loginId = loginId,
            password = password,
            name = name,
            birthDate = birthDate,
            email = email,
        )

        assertAll(
            { assertEquals(loginId, member.loginId) },
            { assertEquals(password, member.password) },
            { assertEquals(name, member.name) },
            { assertEquals(birthDate, member.birthDate) },
            { assertEquals(email, member.email) },
        )
    }
}
