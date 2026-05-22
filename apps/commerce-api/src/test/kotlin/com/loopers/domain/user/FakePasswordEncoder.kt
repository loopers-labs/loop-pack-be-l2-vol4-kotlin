package com.loopers.domain.user

class FakePasswordEncoder : PasswordEncoder {
    override fun encode(raw: RawPassword): String = "encoded(${raw.value})"

    override fun matches(raw: RawPassword, encoded: String): Boolean = encoded == "encoded(${raw.value})"
}
