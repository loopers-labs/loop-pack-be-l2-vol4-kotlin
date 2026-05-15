package com.loopers.account.security

import com.loopers.support.error.CoreException
import org.springframework.security.core.AuthenticationException

class AccountAuthenticationException(
    val coreException: CoreException,
) : AuthenticationException(coreException.message, coreException)
