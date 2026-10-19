import apiPath from '../constants/apiPath'
import { AuthResponse } from '../types/auth.type'
import { getRefreshTokenFromLS } from '../utils/auth'
import http from '../utils/http'

const authApi = {
  registerAccount(body: { email: string; password: string }) {
    return http.post<AuthResponse>(apiPath.register, body)
  },
  loginAccount(body: { email: string; password: string }) {
    return http.post<AuthResponse>(apiPath.login, body)
  },
  // Logout xác thực bằng chính refresh token trong body, không cần access token (API_SPEC §3.3)
  logout() {
    return http.post(apiPath.logout, { refreshToken: getRefreshTokenFromLS() })
  }
}

export default authApi
