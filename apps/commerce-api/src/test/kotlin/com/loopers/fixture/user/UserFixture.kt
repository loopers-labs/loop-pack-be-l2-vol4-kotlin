package com.loopers.fixture.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserSignUpCommand
import java.time.LocalDate

object UserFixture {
    fun createUser(
        loginId: String = "loopers123",
        password: String = "encodedPassword",
        name: String = "gunyoung",
        birthDate: LocalDate = LocalDate.of(1995, 5, 20),
        email: String = "loopers@gmail.com",
    ): User =
        User(
            loginId = loginId,
            password = password,
            name = name,
            birthDate = birthDate,
            email = email,
        )

    fun createUser(
        command: UserSignUpCommand,
        password: String = "encodedPassword",
    ): User =
        createUser(
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
    ): UserSignUpCommand =
        UserSignUpCommand(
            loginId = loginId,
            rawPassword = rawPassword,
            name = name,
            birthDate = birthDate,
            email = email,
        )
}
