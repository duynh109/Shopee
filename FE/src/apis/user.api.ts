import { UpdateProfileBody, User } from '../types/user.type'
import http from '../utils/http'

const URL = 'users/me'

const userApi = {
  getMe() {
    return http.get<User>(URL)
  },

  updateMe(body: UpdateProfileBody) {
    return http.put<User>(URL, body)
  },

  // Chỉ lưu file và trả URL, chưa gắn vào user: phải gọi tiếp updateMe({ avatar: url }) (API_SPEC §4.3)
  uploadAvatar(file: File) {
    const form = new FormData()
    form.append('image', file)
    // Phải khai rõ multipart: http client đặt sẵn 'application/json' cho mọi request, và gặp header đó axios sẽ
    // biến FormData thành chuỗi JSON thay vì gửi file, BE nhận không phải multipart nên trả 415
    return http.post<{ url: string }>(`${URL}/avatar`, form, { headers: { 'Content-Type': 'multipart/form-data' } })
  }
}

export default userApi
