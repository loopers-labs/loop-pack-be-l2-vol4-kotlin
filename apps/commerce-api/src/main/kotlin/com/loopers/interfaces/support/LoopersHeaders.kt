package com.loopers.interfaces.support

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

object LoopersHeaders {
    const val ADMIN_LDAP = "X-Loopers-Ldap"
    const val ADMIN_LDAP_VALUE = "loopers.admin"
    const val LOGIN_ID = "X-Loopers-LoginId"
    const val LOGIN_PW = "X-Loopers-LoginPw"
    const val IDEMPOTENCY_KEY = "Idempotency-Key"

    fun validateAdmin(adminId: String) {
        if (adminId != ADMIN_LDAP_VALUE) {
            throw CoreException(ErrorType.UNAUTHORIZED, "Header '$ADMIN_LDAP' must be '$ADMIN_LDAP_VALUE'.")
        }
    }

    fun validateUser(
        loginId: String,
        password: String,
    ) {
        validateNotBlank(headerName = LOGIN_ID, value = loginId)
        validateNotBlank(headerName = LOGIN_PW, value = password)
    }

    fun validateIdempotencyKey(idempotencyKey: String) {
        validateNotBlank(headerName = IDEMPOTENCY_KEY, value = idempotencyKey)
    }

    private fun validateNotBlank(
        headerName: String,
        value: String,
    ) {
        if (value.isBlank()) {
            throw CoreException(ErrorType.UNAUTHORIZED, "Header '$headerName' must not be blank.")
        }
    }
}
