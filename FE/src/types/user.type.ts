type Role = 'USER' | 'ADMIN'

export interface User {
  id: string
  roles: Role[]
  email: string
  name: string | null
  dateOfBirth: string | null
  address: string | null
  phone: string | null
  avatar: string | null
  createdAt: string
  updatedAt: string
}

// Body của PUT /users/me. Field nào không gửi (undefined) thì BE giữ nguyên; BE không phân biệt "không gửi" với
// null nên không có cách xoá một field về trống (API_SPEC §4.2). Đổi mật khẩu thì gửi kèm password + newPassword.
export interface UpdateProfileBody {
  name?: string
  phone?: string
  address?: string
  dateOfBirth?: string
  avatar?: string
  password?: string
  newPassword?: string
}
