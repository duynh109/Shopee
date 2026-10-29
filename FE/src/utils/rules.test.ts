import { describe, expect, it } from 'vitest'
import { changePasswordSchema, profileSchema, schema } from './rules'

// Luật ở FE phải khớp luật ở BE (API_SPEC §1.7): FE chặn sớm cho người dùng thấy lỗi ngay, nhưng nếu FE chặn lỏng hơn
// thì người dùng qua được form rồi mới nhận 422 từ BE; chặt hơn thì từ chối dữ liệu BE vẫn nhận.
const registerSchema = schema.pick(['email', 'password', 'confirm_password'])
const checkoutSchema = schema.pick(['recipientName', 'recipientPhone', 'shippingAddress', 'note'])

const errorOf = (s: { validateSyncAt: (path: string, value: object) => unknown }, path: string, value: object) => {
  try {
    s.validateSyncAt(path, value)
    return null
  } catch (e) {
    return (e as Error).message
  }
}

describe('đăng ký', () => {
  const valid = { email: 'an.nguyen@gmail.com', password: '123456', confirm_password: '123456' }

  it('dữ liệu đúng thì qua', () => {
    expect(registerSchema.isValidSync(valid)).toBe(true)
  })
  it('email sai định dạng', () => {
    expect(errorOf(registerSchema, 'email', { ...valid, email: 'khong-phai-email' })).toBe('Email không đúng định dạng')
  })
  it('email dài tới 255 kí tự vẫn nhận, như BE', () => {
    const email = 'a'.repeat(243) + '@example.com'
    expect(email).toHaveLength(255)
    expect(errorOf(registerSchema, 'email', { ...valid, email })).toBeNull()
  })
  it('mật khẩu dưới 6 kí tự', () => {
    expect(errorOf(registerSchema, 'password', { ...valid, password: '12345' })).toBe('Độ dài tối thiểu 6 kí tự')
  })
  it('mật khẩu quá 72 byte bị chặn, tính theo byte chứ không theo kí tự', () => {
    const vietnamese = 'ệ'.repeat(25) // 25 kí tự nhưng 75 byte
    expect(errorOf(registerSchema, 'password', { ...valid, password: vietnamese })).toMatch(/72 byte/)
    expect(errorOf(registerSchema, 'password', { ...valid, password: 'a'.repeat(72) })).toBeNull()
  })
  it('nhập lại mật khẩu không khớp', () => {
    expect(errorOf(registerSchema, 'confirm_password', { ...valid, confirm_password: '654321' })).toBe(
      'Nhập lại password không khớp'
    )
  })
})

describe('đặt hàng — số điện thoại Việt Nam: 0 + 9–10 chữ số', () => {
  const base = { recipientName: 'An', shippingAddress: '1 Nguyễn Trãi', note: '' }
  it.each(['0912345678', '09123456789'])('nhận %s', (phone) => {
    expect(errorOf(checkoutSchema, 'recipientPhone', { ...base, recipientPhone: phone })).toBeNull()
  })
  it.each(['912345678', '+84912345678', '091234567', '091234567890'])('từ chối %s', (phone) => {
    expect(errorOf(checkoutSchema, 'recipientPhone', { ...base, recipientPhone: phone })).toBe(
      'Số điện thoại không hợp lệ'
    )
  })
})

describe('đổi mật khẩu và hồ sơ', () => {
  it('mật khẩu mới quá 72 byte bị chặn', () => {
    const value = { password: 'cu123456', newPassword: 'ệ'.repeat(25), confirmNewPassword: 'ệ'.repeat(25) }
    expect(errorOf(changePasswordSchema, 'newPassword', value)).toMatch(/72 byte/)
  })
  it('ngày sinh phải là ngày trong quá khứ; để trống thì được', () => {
    expect(errorOf(profileSchema, 'dateOfBirth', { dateOfBirth: '2999-01-01' })).toBe(
      'Ngày sinh phải là ngày trong quá khứ'
    )
    expect(errorOf(profileSchema, 'dateOfBirth', { dateOfBirth: '' })).toBeNull()
  })
})
