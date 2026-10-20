const path = {
  home: '/',
  profile: '/profile',
  login: '/login',
  register: '/register',
  logout: '/logout',
  productDetail: ':nameId',
  cart: '/cart',
  checkout: '/checkout',
  purchase: '/user/purchase',
  purchaseDetail: '/user/purchase/:id'
} as const

export default path
