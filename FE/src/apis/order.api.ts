import { CreateOrderBody, Order, OrderListConfig } from '../types/order.type'
import { Paginated } from '../types/utils.type'
import http from '../utils/http'

const URL = 'orders'

const orderApi = {
  createOrder(body: CreateOrderBody) {
    return http.post<Order>(URL, body)
  },

  getOrders(config: OrderListConfig) {
    return http.get<Paginated<Order>>(URL, { params: config })
  },

  getOrder(id: string) {
    return http.get<Order>(`${URL}/${id}`)
  },

  cancelOrder(id: string, cancelledReason: string) {
    return http.put(`${URL}/${id}/cancel`, { cancelledReason })
  }
}

export default orderApi
