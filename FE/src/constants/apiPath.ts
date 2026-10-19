// Đường dẫn gọi API (so với baseURL). Tách khỏi constants/path.ts, vốn là route của React Router —
// interceptor so sánh url để biết khi nào lưu token, nên hai thứ này không được dùng chung một hằng số.
const apiPath = {
  register: 'auth/register',
  login: 'auth/login',
  logout: 'auth/logout',
  refreshToken: 'auth/refresh-token'
} as const

export default apiPath
