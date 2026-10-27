import axios, { AxiosError } from 'axios'
import HttpStatusCode from '../constants/httpStatusCode.enum'

export function isAxiosError<T>(error: unknown): error is AxiosError<T> {
  return axios.isAxiosError(error)
}

export function isAxiosUnprocessableEntityError<FormError>(error: unknown): error is AxiosError<FormError> {
  return isAxiosError(error) && error.response?.status === HttpStatusCode.UnprocessableEntity
}

export function formatCurrency(currency: number) {
  return new Intl.NumberFormat('de-DE').format(currency)
}

export function formatNumberToSocialStyle(value: number) {
  return new Intl.NumberFormat('en', {
    notation: 'compact',
    maximumFractionDigits: 1
  })
    .format(value)
    .replace('.', ',')
    .toLowerCase()
}

export const rateSale = (original: number, sale: number) => {
  return Math.round(((original - sale) / original) * 100)
}

// Sản phẩm không giảm giá thì BE trả priceBeforeDiscount = null (cột DB cho phép NULL). Chỉ khi có giá gốc và nó cao hơn
// giá bán mới đáng hiện giá gạch và "-x%". Là type guard nên bên trong nhánh true, TypeScript biết giá gốc là number
export const hasDiscount = (priceBeforeDiscount: number | null, price: number): priceBeforeDiscount is number =>
  priceBeforeDiscount !== null && priceBeforeDiscount > price

// yyyy-MM-dd theo giờ máy, đúng định dạng của <input type="date"> và LocalDate ở BE. Không dùng toISOString():
// nó đổi sang UTC, nên ở múi giờ +7 thì từ 0h đến 7h sáng nó trả ngày hôm qua
export const toISODate = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`

export const removeSpecialCharacter = (str: string) =>
  // eslint-disable-next-line no-useless-escape
  str.replace(/!|@|%|\^|\*|\(|\)|\+|\=|\<|\>|\?|\/|,|\.|\:|\;|\'|\"|\&|\#|\[|\]|~|\$|_|`|-|{|}|\||\\/g, '')

export const generateNameId = ({ name, id }: { name: string; id: string }) => {
  return removeSpecialCharacter(name).replace(/\s/g, '-') + '-i.' + id
}

export const getIdFromNameId = (nameId: string) => {
  const arr = nameId.split('-i.')
  return arr[arr.length - 1]
}

// BE trả thời gian dạng ISO (UTC); hiển thị theo giờ của máy người dùng
export const formatDateTime = (iso: string) =>
  new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(iso))
