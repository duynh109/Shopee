import { ProductListConfig } from '../types/product.type'
import { omitBy, isUndefined } from 'lodash'
import useQueryParams from './useQueryParams'

export type QueryConfig = {
  [key in keyof ProductListConfig]: string
}

export default function useQueryConfig() {
  const queryParams: QueryConfig = useQueryParams()
  const queryConfig: QueryConfig = omitBy(
    {
      page: queryParams.page || '1',
      limit: queryParams.limit || '20',
      sortBy: queryParams.sortBy,
      exclude: queryParams.exclude,
      name: queryParams.name,
      order: queryParams.order,
      priceMax: queryParams.priceMax,
      priceMin: queryParams.priceMin,
      ratingFilter: queryParams.ratingFilter,
      category: queryParams.category
    },
    isUndefined
  )
  return queryConfig
}
