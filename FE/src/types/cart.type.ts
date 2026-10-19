import { ProductSummary } from './product.type'

// Không có field giá: giá luôn đọc từ product.price (API_SPEC §2.4)
export interface CartItem {
  id: string
  quantity: number
  product: ProductSummary
  createdAt: string
  updatedAt: string
}
