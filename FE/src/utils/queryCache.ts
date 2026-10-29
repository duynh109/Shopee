// Query của dữ liệu công khai, ai xem cũng như nhau: giữ lại khi phiên đăng nhập bị xoá. Query nào không có tên ở đây
// đều bị coi là dữ liệu riêng của người đang đăng nhập và bị xoá — thêm query mới mà quên khai thì chỉ phải tải lại,
// không lộ dữ liệu của người trước.
export const PUBLIC_QUERY_KEYS = ['products', 'product', 'categories']

export const isPublicQuery = (queryKey: readonly unknown[]) => PUBLIC_QUERY_KEYS.includes(queryKey[0] as string)
