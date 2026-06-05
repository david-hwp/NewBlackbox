package top.niunaijun.blackboxa.data

import top.niunaijun.blackboxa.bean.dto.*
import top.niunaijun.blackboxa.network.ApiService

class UserRepository(api: ApiService) : BaseRepository(api) {

    suspend fun login(phone: String, password: String): Result<Pair<UserDto, String>> =
        safeApiCall { api.login(LoginRequest(phone, password)) }.map { loginResponse ->
            loginResponse.user to loginResponse.token
        }

    suspend fun register(phone: String, password: String, username: String): Result<UserDto> =
        safeApiCall { api.register(RegisterRequest(phone, password, username)) }

    suspend fun updateUsername(username: String): Result<UserDto> =
        safeApiCall { api.updateUsername(UpdateUsernameRequest(username)) }

    suspend fun updatePassword(oldPwd: String, newPwd: String, confirmPwd: String): Result<Unit> =
        safeApiCall { api.updatePassword(ChangePasswordRequest(oldPwd, newPwd, confirmPwd)) }
}
