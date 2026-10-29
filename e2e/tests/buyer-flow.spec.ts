import { expect, test } from '@playwright/test'

const API = process.env.E2E_API_URL ?? 'http://localhost:8081/api'

// Luồng chính của người mua, đi qua mọi tầng: nginx phục vụ FE, FE gọi BE, BE ghi MySQL do Flyway dựng.
// Dùng tài khoản và sản phẩm của dữ liệu mẫu (profile demo).
test('đăng nhập → mua ngay → đặt hàng → thấy đơn trong Đơn mua', async ({ page, request }) => {
  // Chọn một sản phẩm còn hàng qua API: dữ liệu mẫu có sản phẩm đã hết, bấm bừa sản phẩm đầu tiên thì có thể không mua được.
  const list = await (await request.get(`${API}/products?limit=20`)).json()
  const product = list.items.find((p: { quantity: number }) => p.quantity > 0)
  expect(product, 'dữ liệu mẫu phải có sản phẩm còn hàng').toBeTruthy()

  await page.goto('/login')
  await page.locator('input[name="email"]').fill('an.nguyen@gmail.com')
  await page.locator('input[name="password"]').fill('123456')
  await page.locator('form button[type="submit"]').click()
  await expect(page).toHaveURL('/')
  await expect(page.getByText('an.nguyen@gmail.com')).toBeVisible()

  // Đường dẫn trang sản phẩm là "<tên>-i.<id>"; FE chỉ đọc phần id sau "-i.".
  await page.goto(`/san-pham-i.${product.id}`)
  // So không phân biệt hoa thường (tiêu đề hiện chữ hoa); escape vì tên có thể chứa ( ) + . là kí tự đặc biệt của regex.
  const escapedName = product.name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(new RegExp(escapedName, 'i'))
  await page.getByRole('button', { name: 'Mua ngay' }).click()

  // Đợi thứ chỉ trang đặt hàng mới có, rồi mới kiểm URL và tên sản phẩm. Nếu FE gửi sai id dòng giỏ, trang đặt hàng
  // hiện thoáng qua rồi đẩy về giỏ: URL vẫn kịp khớp /checkout và giỏ cũng có tên sản phẩm, nên hai câu đó đứng
  // trước thì vẫn qua.
  const placeOrder = page.getByRole('button', { name: 'Đặt hàng' })
  await expect(placeOrder).toBeVisible()
  await expect(page).toHaveURL(/\/checkout$/)
  await expect(page.getByText(product.name)).toBeVisible()
  // Trang đặt hàng điền sẵn tên, số điện thoại, địa chỉ từ hồ sơ của tài khoản mẫu.
  await expect(page.locator('input[name="recipientPhone"]')).not.toHaveValue('')
  await placeOrder.click()

  // Đặt xong thì sang trang chi tiết đơn vừa tạo, trạng thái chờ xác nhận.
  await expect(page).toHaveURL(/\/user\/purchase\/\d+/)
  await expect(page.getByText(product.name)).toBeVisible()
  await expect(page.getByText('Chờ xác nhận').first()).toBeVisible()

  // Đơn có trong danh sách Đơn mua.
  await page.goto('/user/purchase')
  await expect(page.getByText(product.name).first()).toBeVisible()
})
