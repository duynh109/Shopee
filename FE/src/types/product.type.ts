import { Category } from './category.type'

// Dạng rút gọn — GET /api/products và product lồng trong dòng giỏ hàng
export interface ProductSummary {
  id: string
  name: string
  image: string
  price: number
  priceBeforeDiscount: number | null
  quantity: number
  sold: number
  view: number
  rating: number
  category: Category
  createdAt: string
  updatedAt: string
}

// Dạng đầy đủ — GET /api/products/{id}: thêm album ảnh và mô tả HTML
export interface Product extends ProductSummary {
  images: string[]
  description: string
}

export interface ProductListConfig {
  page?: number | string
  limit?: number | string
  sortBy?: 'createdAt' | 'view' | 'sold' | 'price'
  order?: 'asc' | 'desc'
  exclude?: string
  ratingFilter?: number | string
  priceMax?: number | string
  priceMin?: number | string
  name?: string
  category?: string
}
