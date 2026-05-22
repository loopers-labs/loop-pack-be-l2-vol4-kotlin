# User Register / Auth — TDD Plan

Plan slices each behavior into the smallest verifiable increment. Each line is one Red → Green cycle.
Source of truth: `docs/user-register/architecture.md`.

## RawPassword value class

- [x] RawPassword accepts an 8-character alphanumeric password
- [x] RawPassword rejects a 7-character password with BAD_REQUEST
- [x] RawPassword rejects a 17-character password with BAD_REQUEST
- [x] RawPassword rejects a password containing a Korean character with BAD_REQUEST
- [x] RawPassword.toString returns "***" so plaintext never appears in logs

## User entity

- [x] User builds with valid loginId, encryptedPassword, name, birthdate, email
- [x] User rejects loginId that does not match alphanumeric 4–20 chars with BAD_REQUEST
- [x] User rejects blank name with BAD_REQUEST
- [x] User rejects email that fails regex shape check with BAD_REQUEST
- [x] User rejects birthdate that is today or in the future with BAD_REQUEST
- [x] User.changePassword overwrites encryptedPassword with the new hash

## PasswordEncoder (BCrypt)

- [x] BCryptPasswordEncoder.encode produces a hash that matches the same RawPassword
- [x] BCryptPasswordEncoder.matches returns false for a different RawPassword

## UserService.register

- [x] register persists a user and returns it with a generated id
- [x] register stores the encrypted password (not the raw value) on the user
- [x] register throws CONFLICT when loginId already exists
- [x] register throws BAD_REQUEST when password contains birthdate as yyyyMMdd digits
- [x] register throws BAD_REQUEST when password contains birthdate as yyMMdd digits
- [x] register throws BAD_REQUEST when password contains birthdate as MMdd digits

## UserService.authenticate

- [x] authenticate returns the user when loginId exists and password matches
- [x] authenticate throws UNAUTHORIZED when no user has that loginId
- [x] authenticate throws UNAUTHORIZED when password does not match

## UserService.changePassword

- [x] changePassword overwrites the stored hash when oldPassword matches
- [x] changePassword throws BAD_REQUEST when oldPassword does not match the stored hash
- [x] changePassword throws BAD_REQUEST when newPassword equals the current stored password
- [x] changePassword throws BAD_REQUEST when newPassword contains birthdate digits

## Auth interceptor / resolver

- [x] AuthenticationInterceptor returns true when handler is not a HandlerMethod
- [x] AuthenticationInterceptor returns true and skips auth when @LoginRequired is absent
- [x] AuthenticationInterceptor throws UNAUTHORIZED when @LoginRequired is present and headers are missing
- [x] AuthenticationInterceptor authenticates and stashes the User in request scope on valid headers
- [x] CurrentUserArgumentResolver returns the User stashed in request scope
- [x] CurrentUserArgumentResolver throws IllegalStateException when no user is stashed

## HTTP API — sign-up

- [x] POST /api/v1/users responds 200 with masked-name MyInfoResponse on valid body
- [x] POST /api/v1/users responds 400 when email fails shape check
- [x] POST /api/v1/users responds 409 when loginId is taken

## HTTP API — get my info

- [x] GET /api/v1/users/me responds 200 with the authenticated user info
- [x] GET /api/v1/users/me responds 401 when credentials are missing
- [x] GET /api/v1/users/me responds 401 when password is wrong

## HTTP API — change password

- [x] PATCH /api/v1/users/me/password responds 200 and changes the stored hash on valid body
- [x] PATCH /api/v1/users/me/password responds 400 when old password does not match
