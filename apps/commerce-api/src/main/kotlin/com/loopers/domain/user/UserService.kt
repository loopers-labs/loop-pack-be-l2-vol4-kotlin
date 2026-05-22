package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional
    fun register(command: UserCommand.Register): User {
        if (userRepository.existsByLoginId(command.loginId)) {
            throw CoreException(ErrorType.CONFLICT, "이미 사용 중인 loginId 입니다.")
        }
        rejectIfPasswordContainsBirthdate(command.rawPassword, command.birthdate)
        val user = User(
            loginId = command.loginId,
            encryptedPassword = passwordEncoder.encode(command.rawPassword),
            name = command.name,
            birthdate = command.birthdate,
            email = command.email,
        )
        return userRepository.save(user)
    }

    @Transactional(readOnly = true)
    fun authenticate(loginId: String, rawPassword: RawPassword): User {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.")
        if (!passwordEncoder.matches(rawPassword, user.encryptedPassword)) {
            throw CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.")
        }
        return user
    }

    @Transactional
    fun changePassword(userId: Long, oldPassword: RawPassword, newPassword: RawPassword) {
        val user = userRepository.findById(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        if (!passwordEncoder.matches(oldPassword, user.encryptedPassword)) {
            throw CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.")
        }
        if (passwordEncoder.matches(newPassword, user.encryptedPassword)) {
            throw CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 기존 비밀번호와 달라야 합니다.")
        }
        rejectIfPasswordContainsBirthdate(newPassword, user.birthdate)
        user.changePassword(passwordEncoder.encode(newPassword))
    }

    private fun rejectIfPasswordContainsBirthdate(password: RawPassword, birthdate: LocalDate) {
        val forbidden = listOf(
            birthdate.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
            birthdate.format(DateTimeFormatter.ofPattern("yyMMdd")),
            birthdate.format(DateTimeFormatter.ofPattern("MMdd")),
        )
        val digitGroups = Regex("\\d+").findAll(password.value).map { it.value }
        if (digitGroups.any { group -> forbidden.any { group.contains(it) } }) {
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일이 포함될 수 없습니다.")
        }
    }
}
