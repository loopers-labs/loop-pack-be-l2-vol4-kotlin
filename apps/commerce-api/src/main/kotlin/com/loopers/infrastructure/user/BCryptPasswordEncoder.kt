package com.loopers.infrastructure.user

import at.favre.lib.crypto.bcrypt.BCrypt
import com.loopers.domain.user.PasswordEncoder
import com.loopers.domain.user.RawPassword
import org.springframework.stereotype.Component

@Component
class BCryptPasswordEncoder : PasswordEncoder {
    override fun encode(raw: RawPassword): String =
        BCrypt.withDefaults().hashToString(COST, raw.value.toCharArray())

    override fun matches(raw: RawPassword, encoded: String): Boolean =
        BCrypt.verifyer().verify(raw.value.toCharArray(), encoded).verified

    companion object {
        private const val COST = 10
    }
}
