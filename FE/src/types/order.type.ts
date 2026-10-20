import { orderStatus } from '../constants/order'

export type OrderStatus = (typeof orderStatus)[keyof typeof orderStatus]
export type PaymentMethod = 'COD' | 'BANK_TRANSFER'
export type PaymentStatus = 'UNPAID' | 'PAID' | 'REFUNDED'

// Tên, ảnh, giá là bản chụp lúc đặt hàng, không đọc động từ products (API_SPEC §2.6)
export interface OrderItem {
  id: string
  productId: string
  productName: string
  productImage: string
  price: number
  priceBeforeDiscount: number
  quantity: number
}

export interface Order {
  id: string
  orderCode: string
  status: OrderStatus
  subtotal: number
  shippingFee: number
  discount: number
  totalAmount: number
  recipientName: string
  recipientPhone: string
  shippingAddress: string
  paymentMethod: PaymentMethod
  paymentStatus: PaymentStatus
  note: string | null
  cancelledReason: string | null
  items: OrderItem[]
  createdAt: string
  updatedAt: string
}

export interface CreateOrderBody {
  cartItemIds: string[]
  recipientName: string
  recipientPhone: string
  shippingAddress: string
  paymentMethod: PaymentMethod
  note?: string
}

export interface OrderListConfig {
  status?: OrderStatus
  page?: number
  limit?: number
}
