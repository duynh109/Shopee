/* eslint-disable react-refresh/only-export-components */
import { Navigate, Outlet, useRoutes } from 'react-router-dom'
import { useContext } from 'react'
import ProductList from './pages/ProductList'
import Login from './pages/Login'
import Register from './pages/Register'
import RegisterLayout from './layouts/RegisterLayout'
import MainLayout from './layouts/MainLayout'
import UserLayout from './layouts/UserLayout'
import Profile from './pages/Profile'
import ChangePassword from './pages/ChangePassword'
import { AppContext } from './contexts/app.context'
import path from './constants/path'
import ProductDetail from './pages/ProductDetail'
import Cart from './pages/Cart'
import Checkout from './pages/Checkout'
import Purchase from './pages/Purchase'
import PurchaseDetail from './pages/PurchaseDetail'

function ProtectedRoute() {
  const { isAuthenticated } = useContext(AppContext)
  return isAuthenticated ? <Outlet /> : <Navigate to='/login' />
}

function RejectedRoute() {
  const { isAuthenticated } = useContext(AppContext)
  return !isAuthenticated ? <Outlet /> : <Navigate to='/' />
}

export default function useRouteElements() {
  const routeElements = useRoutes([
    {
      path: '',
      element: <RejectedRoute />,
      children: [
        {
          path: path.login,
          element: (
            <RegisterLayout>
              <Login />
            </RegisterLayout>
          )
        },
        {
          path: path.register,
          element: (
            <RegisterLayout>
              <Register />
            </RegisterLayout>
          )
        }
      ]
    },
    {
      path: '',
      element: <ProtectedRoute />,
      children: [
        {
          // Layout route (không có path): chỉ để bọc các route con trong khung có menu tài khoản bên trái
          element: (
            <MainLayout>
              <UserLayout />
            </MainLayout>
          ),
          children: [
            { path: path.profile, element: <Profile /> },
            { path: path.password, element: <ChangePassword /> },
            { path: path.purchase, element: <Purchase /> }
          ]
        },
        {
          path: path.cart,
          element: (
            <MainLayout>
              <Cart />
            </MainLayout>
          )
        },
        {
          path: path.checkout,
          element: (
            <MainLayout>
              <Checkout />
            </MainLayout>
          )
        },
        {
          // Chi tiết đơn không nằm trong khung tài khoản: như Shopee, trang này dùng hết chiều rộng
          path: path.purchaseDetail,
          element: (
            <MainLayout>
              <PurchaseDetail />
            </MainLayout>
          )
        }
      ]
    },
    {
      path: '',
      index: true,
      element: (
        <MainLayout>
          <ProductList />
        </MainLayout>
      )
    },
    {
      path: path.productDetail,
      element: (
        <MainLayout>
          <ProductDetail />
        </MainLayout>
      )
    }
  ])
  return routeElements
}
