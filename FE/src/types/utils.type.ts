export interface Paginated<T> {
  items: T[]
  page: number
  limit: number
  totalItems: number
  totalPages: number
}

// RFC 9457 — hình dạng chung của MỌI response lỗi
export interface ProblemDetail {
  type: string
  title: string
  status: number
  detail?: string
  errors?: Record<string, string>
}

// Cú pháp '-?' để loại bỏ undefined của key optional
export type NoUndefinedField<T> = {
  [P in keyof T]-?: NoUndefinedField<NonNullable<T[P]>>
}
