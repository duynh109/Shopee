import { Category } from '../types/category.type'
import http from '../utils/http'

const URL = 'categories'

const categoryApi = {
  getCategories() {
    return http.get<Category[]>(URL)
  }
}

export default categoryApi
