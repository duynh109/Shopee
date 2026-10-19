import { Product, ProductListConfig, ProductSummary } from '../types/product.type'
import { Paginated } from '../types/utils.type'
import http from '../utils/http'

const URL = 'products'

const productApi = {
  getProducts(params: ProductListConfig) {
    return http.get<Paginated<ProductSummary>>(URL, {
      params
    })
  },
  getProductDetail(id: string) {
    return http.get<Product>(`${URL}/${id}`)
  }
}

export default productApi
