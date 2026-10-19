// `type` của ProblemDetail mà FE rẽ nhánh theo. Chuỗi do BE đặt và coi như bất biến (API_SPEC §1.6),
// nên phải trùng từng ký tự — đừng sửa câu chữ ở đây.
const errorType = {
  // Access token hết hạn: FE âm thầm đổi token mới rồi gửi lại request
  tokenExpired: '/errors/token-expired',
  // Access token hỏng/sai chữ ký/không gửi: đưa về màn đăng nhập
  tokenInvalid: '/errors/token-invalid',
  // Refresh token không dùng được: đưa về màn đăng nhập. Phải khác tokenExpired, nếu không FE sẽ gọi refresh mãi
  refreshTokenInvalid: '/errors/refresh-token-invalid'
} as const

export default errorType
