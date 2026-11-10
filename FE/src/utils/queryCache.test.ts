import { describe, expect, it } from 'vitest'
import { isPublicQuery } from './queryCache'

// Khi phiên bị xoá, query công khai được giữ (trang đang mở không bị trắng), query riêng bị xoá (người đăng nhập sau
// không thấy dữ liệu của người trước). Key lấy đúng như các trang đang dùng.
describe('isPublicQuery', () => {
  it.each([[['products', { page: '1', limit: '20' }]], [['product', '12']], [['categories']]])(
    'giữ dữ liệu công khai %j',
    (key) => {
      expect(isPublicQuery(key)).toBe(true)
    }
  )

  it.each([[['cart']], [['orders', { status: 'PENDING' }]], [['order', '5']], [['me']]])(
    'xoá dữ liệu riêng %j',
    (key) => {
      expect(isPublicQuery(key)).toBe(false)
    }
  )

  it('query mới chưa khai thì coi là riêng — quên khai chỉ phải tải lại, không lộ dữ liệu', () => {
    expect(isPublicQuery(['vouchers'])).toBe(false)
  })
})
