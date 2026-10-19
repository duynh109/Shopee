import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import { toast } from 'react-toastify'
import HttpStatusCode from '../constants/httpStatusCode.enum'
import apiPath from '../constants/apiPath'
import errorType from '../constants/errorType'
import { AuthResponse } from '../types/auth.type'
import { ProblemDetail } from '../types/utils.type'
import {
  LocalStorageEventTarget,
  clearLS,
  getAccessTokenFromLS,
  getRefreshTokenFromLS,
  setAccessTokenToLS,
  setProfileToLS,
  setRefreshTokenToLS
} from './auth'
import { isAxiosError } from './utils'

const BASE_URL = 'http://localhost:8081/api/'
// Mọi tab của app xếp hàng qua khoá này khi đổi token (Web Locks API)
const REFRESH_LOCK_NAME = 'refresh-token'
// Nhiều request cùng bị đăng xuất một lúc thì chỉ hiện một toast
const SESSION_EXPIRED_TOAST_ID = 'session-expired'

// axios không có chỗ sẵn để đánh dấu "request này đã được gửi lại rồi", nên tự thêm cờ
type RetryableConfig = InternalAxiosRequestConfig & { _retry?: boolean }

// Refresh token hỏng thì BE trả 401 (không dùng được) hoặc 422 (thiếu). Lỗi mạng, 5xx, timeout thì không phải
// lỗi của refresh token, nên không được vì thế mà đăng xuất người dùng.
const isRefreshTokenRejected = (error: unknown) =>
  isAxiosError(error) &&
  (error.response?.status === HttpStatusCode.Unauthorized ||
    error.response?.status === HttpStatusCode.UnprocessableEntity)

class Http {
  instance: AxiosInstance
  // Client riêng cho đúng một việc là gọi auth/refresh-token: không gắn access token, không qua interceptor —
  // nếu qua interceptor thì lỗi của chính request refresh lại kích hoạt refresh tiếp.
  private refreshClient: AxiosInstance
  private accessToken: string
  // Request đổi token đang bay. Nhiều request cùng nhận token-expired thì cùng chờ promise này.
  private refreshing: Promise<string | null> | null = null

  constructor() {
    /**
     * Trick giúp tăng tốc độ truy xuất bộ nhớ: mỗi lần lấy access token từ localstorage thì sẽ truy xuất
     * đến bộ nhớ sẽ chậm hơn so với truy xuất đến ram để lấy biến accessToken vì vậy thêm biến accessToken sẽ giúp
     * tiết kiệm thời gian truy xuất. Chỉ lấy access token từ localstorage 1 lần duy nhất ở constructor vì constructor chỉ chạy 1 lần
     */

    this.accessToken = getAccessTokenFromLS()
    this.instance = axios.create({
      baseURL: BASE_URL,
      timeout: 10000,
      headers: {
        'Content-Type': 'application/json'
      }
    })
    this.refreshClient = axios.create({
      baseURL: BASE_URL,
      timeout: 10000,
      headers: {
        'Content-Type': 'application/json'
      }
    })

    // Phiên bị xoá (đăng xuất, hoặc refresh token hỏng) thì bản sao trong RAM cũng phải bỏ
    LocalStorageEventTarget.addEventListener('clearLS', () => {
      this.accessToken = ''
    })

    this.instance.interceptors.request.use(
      (config) => {
        if (this.accessToken) {
          config.headers.Authorization = `Bearer ${this.accessToken}`
          return config
        }
        return config
      },
      (error) => {
        return Promise.reject(error)
      }
    )

    this.instance.interceptors.response.use(
      (response) => {
        const { url } = response.config
        if (url === apiPath.login || url === apiPath.register) {
          const data = response.data as AuthResponse
          this.saveTokens(data)
          setProfileToLS(data.user)
        }
        return response
      },
      async (error: AxiosError<ProblemDetail>) => {
        const config = error.config as RetryableConfig | undefined
        const problem = error.response?.data

        if (error.response?.status === HttpStatusCode.Unauthorized && config) {
          // Access token hết hạn: đổi token mới rồi gửi lại đúng request vừa hỏng. Người dùng không thấy gì.
          // `_retry` chặn việc gửi lại lần hai: gửi lại mà vẫn hết hạn thì thôi, không lặp mãi.
          if (problem?.type === errorType.tokenExpired && !config._retry) {
            config._retry = true
            try {
              const accessToken = await this.refreshAccessToken()
              if (accessToken) {
                config.headers.Authorization = `Bearer ${accessToken}`
                return this.instance(config)
              }
              // null: phiên đã kết thúc và người dùng đã được báo rồi
              return Promise.reject(error)
            } catch (refreshError) {
              // Lỗi tạm thời lúc đổi token (mất mạng, BE lỗi 5xx): phiên vẫn còn, chỉ báo lỗi
              toast.error(
                (isAxiosError<ProblemDetail>(refreshError) && refreshError.response?.data?.title) ||
                  (refreshError as Error).message
              )
              return Promise.reject(refreshError)
            }
          }
          // Token hỏng hẳn: không cứu được, về màn đăng nhập
          if (problem?.type === errorType.tokenInvalid || problem?.type === errorType.refreshTokenInvalid) {
            this.endSession()
            return Promise.reject(error)
          }
        }

        if (error.response?.status !== HttpStatusCode.UnprocessableEntity) {
          // Lỗi mạng (không có response) thì không có title, dùng message của axios
          toast.error(problem?.title || error.message)
        }
        return Promise.reject(error)
      }
    )
  }

  private saveTokens({ accessToken, refreshToken }: Pick<AuthResponse, 'accessToken' | 'refreshToken'>) {
    this.accessToken = accessToken
    setAccessTokenToLS(accessToken)
    setRefreshTokenToLS(refreshToken)
  }

  // Xoá phiên ở máy này; AppContext nghe tín hiệu clearLS nên tự chuyển về trạng thái chưa đăng nhập
  private endSession() {
    clearLS()
    toast.error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.', { toastId: SESSION_EXPIRED_TOAST_ID })
  }

  /**
   * Trả về access token mới, hoặc `null` nếu phiên đã hết (refresh token hỏng — đã xoá phiên và báo người dùng).
   * Ném lỗi chỉ khi gặp lỗi tạm thời (mạng, 5xx).
   *
   * Chỉ một request refresh tại một thời điểm: BE xoay vòng refresh token, nên gửi song song hai lần với cùng một
   * token thì lần sau bị coi là token bị dùng lại, và người dùng bị đăng xuất khỏi mọi thiết bị (API_SPEC §3.4).
   */
  private refreshAccessToken(): Promise<string | null> {
    if (!this.refreshing) {
      this.refreshing = this.requestNewTokens().finally(() => {
        this.refreshing = null
      })
    }
    return this.refreshing
  }

  private requestNewTokens(): Promise<string | null> {
    // Access token mà request vừa hỏng đã dùng
    const staleAccessToken = this.accessToken

    const run = async () => {
      // Tab khác có thể đã đổi xong trong lúc tab này chờ khoá: localStorage đã có access token mới hơn thì dùng
      // luôn, đừng đổi thêm lần nữa
      const latestAccessToken = getAccessTokenFromLS()
      if (latestAccessToken && latestAccessToken !== staleAccessToken) {
        this.accessToken = latestAccessToken
        return latestAccessToken
      }

      // Đọc refresh token từ localStorage chứ không giữ trong RAM: tab khác có thể đã xoay vòng, khi đó token
      // trong RAM của tab này đã bị thu hồi, gửi lên là bị coi như token bị lộ
      const refreshToken = getRefreshTokenFromLS()
      if (!refreshToken) {
        this.endSession()
        return null
      }
      try {
        const { data } = await this.refreshClient.post<AuthResponse>(apiPath.refreshToken, { refreshToken })
        this.saveTokens(data)
        return data.accessToken
      } catch (error) {
        if (isRefreshTokenRejected(error)) {
          this.endSession()
          return null
        }
        throw error
      }
    }

    // Web Locks có trong mọi trình duyệt hiện đại (chỉ ở ngữ cảnh https hoặc localhost). Không có thì chạy thẳng,
    // vẫn đúng trong một tab.
    return 'locks' in navigator ? navigator.locks.request(REFRESH_LOCK_NAME, run) : run()
  }
}

const http = new Http().instance

export default http
