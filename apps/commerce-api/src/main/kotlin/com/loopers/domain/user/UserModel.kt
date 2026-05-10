package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class UserModel(
    loginId: String,
    password: String,
    name: String,
    birthDate: String,
    email: String,
) : BaseEntity() {

    val loginId: String = loginId
    val birthDate: String = birthDate
    val email: String = email

    var name: String = name
        protected set

    var password: String = password
        protected set

    init {
        // loginId: 영문 + 숫자만 허용
        if (loginId.isBlank())
            throw CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 필수입니다.")
        if (!loginId.matches(Regex("^[a-zA-Z0-9]+$")))
            throw CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 사용 가능합니다.")

        // name: 한글 또는 영문, 2~20자
        if (name.isBlank())
            throw CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.")
        if (name.length < 2 || name.length > 20)
            throw CoreException(ErrorType.BAD_REQUEST, "이름은 2자~20자 이내입니다.")
        if (!name.matches(Regex("^[가-힣a-zA-Z]+$")))
            throw CoreException(ErrorType.BAD_REQUEST, "이름은 한글 또는 영문만 가능합니다.")

        // birthDate: yyyyMMdd 형식 (월: 01~12, 일: 01~31)
        if (birthDate.isBlank())
            throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 필수입니다.")
        if (!birthDate.matches(Regex("^\\d{4}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])$")))
            throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 yyyyMMdd 형식이어야 합니다.")

        // email: 이메일 형식
        if (email.isBlank())
            throw CoreException(ErrorType.BAD_REQUEST, "이메일은 필수입니다.")
        if (!email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")))
            throw CoreException(ErrorType.BAD_REQUEST, "유효한 이메일 형식이 아닙니다.")

        // password: 8~16자, 영문 대소문자/숫자/특수문자만 허용
        if (password.isBlank())
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 필수입니다.")
        if (!password.matches(Regex("^[a-zA-Z0-9!@#\$%^&*()_+\\-=\\[\\]{};':\",./<>?]{8,16}$")))
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.")
        if (password.contains(birthDate))
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.")
    }

}
