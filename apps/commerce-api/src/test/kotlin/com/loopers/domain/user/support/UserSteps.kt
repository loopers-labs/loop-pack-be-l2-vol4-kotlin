package com.loopers.domain.user.support

import com.loopers.domain.user.application.command.UserSignUpCommand
import com.loopers.domain.user.model.UserModel
import com.loopers.domain.user.port.PasswordEncoder
import com.loopers.domain.user.presentation.request.SignUpRequest
import com.loopers.domain.user.vo.Birthday
import com.loopers.domain.user.vo.Email
import com.loopers.domain.user.vo.LoginId
import com.loopers.domain.user.vo.Name
import com.loopers.domain.user.vo.Password
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate

class UserSteps {
    companion object {
        const val 기본_로그인_ID: String = "user1234"
        const val 기본_비밀번호: String = "Password1!"
        const val 기본_이름: String = "홍길동"
        const val 기본_이메일: String = "user@example.com"
        val 기본_생년월일: LocalDate = LocalDate.of(1990, 5, 14)

        fun 기본_인코더(): PasswordEncoder = mockk {
            every { encode(any()) } answers { "ENC(" + firstArg<String>() + ")" }
            every { matches(any(), any()) } answers { secondArg<String>() == "ENC(" + firstArg<String>() + ")" }
        }

        fun 비밀번호_생성(
            rawPassword: String = 기본_비밀번호,
            birthday: LocalDate = 기본_생년월일,
            encoder: PasswordEncoder = 기본_인코더(),
        ): Password = Password.of(rawPassword, Birthday.of(birthday), encoder)

        fun 회원_도메인_생성(
            id: Long = 0,
            loginId: String = 기본_로그인_ID,
            rawPassword: String = 기본_비밀번호,
            name: String = 기본_이름,
            birthday: LocalDate = 기본_생년월일,
            email: String = 기본_이메일,
            encoder: PasswordEncoder = 기본_인코더(),
        ): UserModel {
            val birthdayVo = Birthday.of(birthday)
            return UserModel(
                id = id,
                loginId = LoginId.of(loginId),
                password = Password.of(rawPassword, birthdayVo, encoder),
                name = Name.of(name),
                birthday = birthdayVo,
                email = Email.of(email),
            )
        }

        fun 저장된_회원_도메인_생성(
            id: Long,
            loginId: String = 기본_로그인_ID,
            encodedPassword: String,
            name: String = 기본_이름,
            birthday: LocalDate = 기본_생년월일,
            email: String = 기본_이메일,
        ): UserModel = UserModel(
            id = id,
            loginId = LoginId.of(loginId),
            password = Password.fromEncoded(encodedPassword),
            name = Name.of(name),
            birthday = Birthday.of(birthday),
            email = Email.of(email),
        )

        fun 사용자_회원가입(
            loginId: String = 기본_로그인_ID,
            rawPassword: String = 기본_비밀번호,
            name: String = 기본_이름,
            birthday: LocalDate = 기본_생년월일,
            email: String = 기본_이메일,
        ): UserSignUpCommand = UserSignUpCommand(
            loginId = loginId,
            rawPassword = rawPassword,
            name = name,
            birthday = birthday,
            email = email,
        )

        fun 회원가입_요청_생성(
            loginId: String = 기본_로그인_ID,
            password: String = 기본_비밀번호,
            name: String = 기본_이름,
            birthday: LocalDate = 기본_생년월일,
            email: String = 기본_이메일,
        ): SignUpRequest = SignUpRequest(
            loginId = loginId,
            password = password,
            name = name,
            birthday = birthday,
            email = email,
        )
    }
}
