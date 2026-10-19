import { CartItem } from '../types/cart.type'
import http from '../utils/http'

const URL = 'cart'

const cartApi = {
  getCart() {
    return http.get<CartItem[]>(URL)
  },
  // Sản phẩm đã có trong giỏ thì BE cộng dồn vào dòng cũ (API_SPEC §7.2)
  addToCart(body: { productId: string; quantity: number }) {
    return http.post<CartItem>(`${URL}/items`, body)
  },
  // quantity là giá trị tuyệt đối, không phải cộng thêm (API_SPEC §7.3)
  updateCartItem(id: string, body: { quantity: number }) {
    return http.put<CartItem>(`${URL}/items/${id}`, body)
  },
  deleteCartItem(id: string) {
    return http.delete<void>(`${URL}/items/${id}`)
  },
  deleteCartItems(ids: string[]) {
    return http.delete<void>(`${URL}/items`, {
      params: { ids: ids.join(',') }
    })
  }
}

export default cartApi
