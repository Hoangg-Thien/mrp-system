import { dateTime, money } from '../../utils/format'
import { activeOptions, emptyText } from '../../utils/statusMap'

/**
 * Cấu hình màn hình danh mục (W2). `sort` trong columns phải nằm trong whitelist
 * của backend: categories(id,name), products(id,name,sellingPrice,createdAt),
 * suppliers(id,name,phone,email), materials(id,code,name,standardCost,createdAt).
 */
export const resources = {
  categories: {
    title: 'Danh mục',
    singular: 'danh mục',
    subtitle: 'Nhóm sản phẩm dùng cho quản trị và kế hoạch MRP',
    path: '/api/categories',
    defaultSort: { sort: 'name', direction: 'asc' },
    hasActive: false,
    filters: [{ name: 'keyword', type: 'search', placeholder: 'Tìm theo tên danh mục…', width: 300 }],
    fields: [
      { name: 'name', label: 'Tên danh mục', type: 'text', required: true, max: 150 },
      { name: 'description', label: 'Mô tả', type: 'textarea' },
    ],
    columns: [
      { title: 'ID', dataIndex: 'id', width: 80, sorter: true },
      { title: 'Tên danh mục', dataIndex: 'name', sorter: true },
      { title: 'Mô tả', dataIndex: 'description', render: emptyText },
    ],
  },
  products: {
    title: 'Sản phẩm',
    singular: 'sản phẩm',
    subtitle: 'Tìm kiếm nâng cao theo danh mục, khoảng giá và trạng thái',
    path: '/api/products',
    defaultSort: { sort: 'name', direction: 'asc' },
    hasActive: true,
    filters: [
      { name: 'keyword', type: 'search', placeholder: 'Tìm theo tên / mô tả…', width: 260 },
      { name: 'categoryId', type: 'select', placeholder: 'Danh mục', width: 200 },
      { name: 'minPrice', type: 'number', placeholder: 'Giá từ', width: 140 },
      { name: 'maxPrice', type: 'number', placeholder: 'Giá đến', width: 140 },
      { name: 'active', type: 'select', placeholder: 'Trạng thái (mặc định: đang bán)', width: 240, options: activeOptions },
    ],
    fields: [
      { name: 'categoryId', label: 'Danh mục', type: 'category', required: true },
      { name: 'name', label: 'Tên sản phẩm', type: 'text', required: true, max: 200 },
      { name: 'description', label: 'Mô tả', type: 'textarea' },
      { name: 'unit', label: 'Đơn vị tính', type: 'text', required: true, max: 50 },
      { name: 'sellingPrice', label: 'Giá bán', type: 'money', required: true },
    ],
    columns: [
      { title: 'ID', dataIndex: 'id', width: 80, sorter: true },
      { title: 'Tên sản phẩm', dataIndex: 'name', sorter: true },
      { title: 'Danh mục', dataIndex: 'categoryId', width: 180 },
      { title: 'Đơn vị', dataIndex: 'unit', width: 100 },
      { title: 'Giá bán', dataIndex: 'sellingPrice', width: 150, align: 'right', sorter: true, render: money },
      { title: 'Trạng thái', dataIndex: 'active', width: 150 },
      { title: 'Ngày tạo', dataIndex: 'createdAt', width: 170, sorter: true, render: dateTime },
    ],
  },
  suppliers: {
    title: 'Nhà cung cấp',
    singular: 'nhà cung cấp',
    subtitle: 'Tìm theo tên, số điện thoại hoặc email',
    path: '/api/suppliers',
    defaultSort: { sort: 'name', direction: 'asc' },
    hasActive: false,
    filters: [
      { name: 'keyword', type: 'search', placeholder: 'Tìm nhanh (tên / SĐT / email)…', width: 280 },
      { name: 'name', type: 'search', placeholder: 'Tên nhà cung cấp…', width: 200 },
      { name: 'phone', type: 'search', placeholder: 'Số điện thoại…', width: 170 },
      { name: 'email', type: 'search', placeholder: 'Email…', width: 200 },
    ],
    fields: [
      { name: 'name', label: 'Tên nhà cung cấp', type: 'text', required: true, max: 200 },
      { name: 'phone', label: 'Số điện thoại', type: 'text', max: 20 },
      { name: 'email', label: 'Email', type: 'email', max: 150 },
      { name: 'address', label: 'Địa chỉ', type: 'textarea' },
    ],
    columns: [
      { title: 'ID', dataIndex: 'id', width: 80, sorter: true },
      { title: 'Tên nhà cung cấp', dataIndex: 'name', sorter: true },
      { title: 'Số điện thoại', dataIndex: 'phone', width: 150, sorter: true, render: emptyText },
      { title: 'Email', dataIndex: 'email', width: 220, sorter: true, render: emptyText },
      { title: 'Địa chỉ', dataIndex: 'address', render: emptyText },
    ],
  },
  materials: {
    title: 'Nguyên vật liệu',
    singular: 'nguyên vật liệu',
    subtitle: 'Dữ liệu đầu vào cho BOM và kế hoạch MRP',
    path: '/api/materials',
    defaultSort: { sort: 'name', direction: 'asc' },
    hasActive: true,
    filters: [
      { name: 'keyword', type: 'search', placeholder: 'Tìm theo mã / tên…', width: 280 },
      { name: 'active', type: 'select', placeholder: 'Trạng thái (mặc định: tất cả)', width: 230, options: activeOptions },
    ],
    fields: [
      { name: 'code', label: 'Mã nguyên vật liệu', type: 'text', required: true, max: 50 },
      { name: 'name', label: 'Tên nguyên vật liệu', type: 'text', required: true, max: 200 },
      { name: 'unit', label: 'Đơn vị tính', type: 'text', required: true, max: 50 },
      { name: 'standardCost', label: 'Giá tiêu chuẩn', type: 'money', required: true },
    ],
    columns: [
      { title: 'ID', dataIndex: 'id', width: 80, sorter: true },
      { title: 'Mã NVL', dataIndex: 'code', width: 140, sorter: true },
      { title: 'Tên nguyên vật liệu', dataIndex: 'name', sorter: true },
      { title: 'Đơn vị', dataIndex: 'unit', width: 100 },
      { title: 'Giá tiêu chuẩn', dataIndex: 'standardCost', width: 160, align: 'right', sorter: true, render: money },
      { title: 'Trạng thái', dataIndex: 'active', width: 150 },
      { title: 'Ngày tạo', dataIndex: 'createdAt', width: 170, sorter: true, render: dateTime },
    ],
  },
}
