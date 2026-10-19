import { User } from './user.type'

export interface AuthResponse {
  accessToken: string
  expires: number
  refreshToken: string
  user: User
}
