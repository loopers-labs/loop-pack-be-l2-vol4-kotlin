package com.loopers.domain.user.exception

import com.loopers.domain.user.constant.UserErrorMessages

class DuplicateLoginIdException(
    loginId: String,
    cause: Throwable? = null,
) : UserDomainException(UserErrorMessages.DUPLICATE_LOGIN_ID, cause)
