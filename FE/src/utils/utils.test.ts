import { describe, expect, it } from 'vitest'
import {
  formatCurrency,
  formatNumberToSocialStyle,
  generateNameId,
  getIdFromNameId,
  hasDiscount,
  rateSale,
  toISODate
} from './utils'

describe('formatCurrency', () => {
  it('ngăn cách hàng nghìn bằng dấu chấm, kiểu Việt Nam', () => {
    expect(formatCurrency(1234567)).toBe('1.234.567')
    expect(formatCurrency(0)).toBe('0')
  })
})

describe('formatNumberToSocialStyle', () => {
  it('rút gọn số lớn, dấu phẩy thập phân, chữ thường', () => {
    expect(formatNumberToSocialStyle(999)).toBe('999')
    expect(formatNumberToSocialStyle(1234)).toBe('1,2k')
    expect(formatNumberToSocialStyle(1_500_000)).toBe('1,5m')
  })
})

describe('hasDiscount — giá gốc null nghĩa là không giảm giá', () => {
  it('null thì không giảm (tránh hiện ₫0 và -Infinity%)', () => {
    expect(hasDiscount(null, 100_000)).toBe(false)
  })
  it('giá gốc bằng hoặc thấp hơn giá bán thì không giảm', () => {
    expect(hasDiscount(100_000, 100_000)).toBe(false)
    expect(hasDiscount(90_000, 100_000)).toBe(false)
  })
  it('giá gốc cao hơn giá bán thì có giảm', () => {
    expect(hasDiscount(120_000, 100_000)).toBe(true)
  })
})

describe('rateSale', () => {
  it('phần trăm giảm, làm tròn', () => {
    expect(rateSale(200_000, 150_000)).toBe(25)
    expect(rateSale(269_000, 219_000)).toBe(19)
  })
})

describe('generateNameId / getIdFromNameId — đường dẫn trang sản phẩm', () => {
  it('bỏ ký tự đặc biệt, thay khoảng trắng bằng gạch ngang, giữ chữ có dấu', () => {
    expect(generateNameId({ name: 'Áo thun (nam) 100%', id: '12' })).toBe('Áo-thun-nam-100-i.12')
  })
  it('lấy lại đúng id, kể cả khi tên có sẵn "-i."', () => {
    expect(getIdFromNameId(generateNameId({ name: 'Wi-i.Fi Router', id: '7' }))).toBe('7')
  })
})

describe('toISODate', () => {
  it('yyyy-MM-dd theo giờ máy, có số 0 đứng trước', () => {
    expect(toISODate(new Date(2026, 0, 5))).toBe('2026-01-05')
  })
})
