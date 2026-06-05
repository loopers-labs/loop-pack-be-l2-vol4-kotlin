package com.loopers.domain.user.unit

import com.loopers.domain.user.exception.InvalidPasswordException
import com.loopers.domain.user.exception.InvalidUserException
import com.loopers.domain.user.support.UserSteps.Companion.기본_로그인_ID
import com.loopers.domain.user.support.UserSteps.Companion.기본_비밀번호
import com.loopers.domain.user.support.UserSteps.Companion.기본_생년월일
import com.loopers.domain.user.support.UserSteps.Companion.기본_이름
import com.loopers.domain.user.support.UserSteps.Companion.기본_이메일
import com.loopers.domain.user.support.UserSteps.Companion.기본_인코더
import com.loopers.domain.user.support.UserSteps.Companion.비밀번호_생성
import com.loopers.domain.user.support.UserSteps.Companion.회원_도메인_생성
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class UserTest {
    @Test
    fun `모든_필드가_유효하면_회원이_정상_생성된다`() {
        val user = 회원_도메인_생성()

        assertThat(user.loginId.value).isEqualTo(기본_로그인_ID)
        assertThat(user.name.value).isEqualTo(기본_이름)
        assertThat(user.birthday.value).isEqualTo(기본_생년월일)
        assertThat(user.email.value).isEqualTo(기본_이메일)
        assertThat(user.password.matches(기본_비밀번호, 기본_인코더())).isTrue()
    }

    @Test
    fun `로그인ID에_영숫자_외_문자가_있으면_회원_생성이_불가하다`() {
        assertThrows<InvalidUserException> { 회원_도메인_생성(loginId = "한글ID!") }
    }

    @Test
    fun `로그인ID가_4자_미만이면_회원_생성이_불가하다`() {
        assertThrows<InvalidUserException> { 회원_도메인_생성(loginId = "abc") }
    }

    @Test
    fun `로그인ID가_20자_초과면_회원_생성이_불가하다`() {
        assertThrows<InvalidUserException> { 회원_도메인_생성(loginId = "a".repeat(21)) }
    }

    @Test
    fun `이름이_공백이면_회원_생성이_불가하다`() {
        assertThrows<InvalidUserException> { 회원_도메인_생성(name = "   ") }
    }

    @Test
    fun `이메일_형식이_잘못되면_회원_생성이_불가하다`() {
        assertThrows<InvalidUserException> { 회원_도메인_생성(email = "not-an-email") }
    }

    @Test
    fun `생년월일이_미래_일자면_회원_생성이_불가하다`() {
        assertThrows<InvalidUserException> { 회원_도메인_생성(birthday = LocalDate.now().plusDays(1)) }
    }

    @Test
    fun `비밀번호가_8자_미만이면_비밀번호_생성이_불가하다`() {
        assertThrows<InvalidPasswordException> { 비밀번호_생성(rawPassword = "Ab1!abc") }
    }

    @Test
    fun `비밀번호가_16자_초과면_비밀번호_생성이_불가하다`() {
        assertThrows<InvalidPasswordException> { 비밀번호_생성(rawPassword = "Ab1!" + "a".repeat(14)) }
    }

    @Test
    fun `비밀번호에_대문자가_없으면_비밀번호_생성이_불가하다`() {
        assertThrows<InvalidPasswordException> { 비밀번호_생성(rawPassword = "password1!") }
    }

    @Test
    fun `비밀번호에_소문자가_없으면_비밀번호_생성이_불가하다`() {
        assertThrows<InvalidPasswordException> { 비밀번호_생성(rawPassword = "PASSWORD1!") }
    }

    @Test
    fun `비밀번호에_숫자가_없으면_비밀번호_생성이_불가하다`() {
        assertThrows<InvalidPasswordException> { 비밀번호_생성(rawPassword = "Password!!") }
    }

    @Test
    fun `비밀번호에_특수문자가_없으면_비밀번호_생성이_불가하다`() {
        assertThrows<InvalidPasswordException> { 비밀번호_생성(rawPassword = "Password11") }
    }

    @Test
    fun `비밀번호에_한글이_포함되면_비밀번호_생성이_불가하다`() {
        assertThrows<InvalidPasswordException> { 비밀번호_생성(rawPassword = "Pass한글1!") }
    }

    @Test
    fun `비밀번호에_허용되지_않은_특수문자가_있으면_비밀번호_생성이_불가하다`() {
        assertThrows<InvalidPasswordException> { 비밀번호_생성(rawPassword = "Password1~") }
    }

    @Test
    fun `비밀번호에_yyyyMMdd_생년월일_토큰이_포함되면_비밀번호_생성이_불가하다`() {
        assertThrows<InvalidPasswordException> {
            비밀번호_생성(rawPassword = "Pw19900514!", birthday = LocalDate.of(1990, 5, 14))
        }
    }

    @Test
    fun `비밀번호에_yyMMdd_생년월일_토큰이_포함되면_비밀번호_생성이_불가하다`() {
        assertThrows<InvalidPasswordException> {
            비밀번호_생성(rawPassword = "Pw900514A!", birthday = LocalDate.of(1990, 5, 14))
        }
    }

    @Test
    fun `비밀번호에_MMdd_생년월일_토큰이_포함되면_비밀번호_생성이_불가하다`() {
        assertThrows<InvalidPasswordException> {
            비밀번호_생성(rawPassword = "Pw0514AB!", birthday = LocalDate.of(1990, 5, 14))
        }
    }
}
