package com.loopers.fixture.member

import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberSignUpCommand
import java.time.LocalDate

object MemberFixture {
    fun createMember(
        loginId: String = "loopers123",
        password: String = "encodedPassword",
        name: String = "gunyoung",
        birthDate: LocalDate = LocalDate.of(1995, 5, 20),
        email: String = "loopers@gmail.com",
    ): Member =
        Member(
            loginId = loginId,
            password = password,
            name = name,
            birthDate = birthDate,
            email = email,
        )

    fun createMember(
        command: MemberSignUpCommand,
        password: String = "encodedPassword",
    ): Member =
        createMember(
            loginId = command.loginId,
            password = password,
            name = command.name,
            birthDate = command.birthDate,
            email = command.email,
        )

    fun createSignUpCommand(
        loginId: String = "loopers123",
        rawPassword: String = "Loopers123!",
        name: String = "gunyoung",
        birthDate: LocalDate = LocalDate.of(1995, 5, 20),
        email: String = "loopers@gmail.com",
    ): MemberSignUpCommand =
        MemberSignUpCommand(
            loginId = loginId,
            rawPassword = rawPassword,
            name = name,
            birthDate = birthDate,
            email = email,
        )
}
