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
