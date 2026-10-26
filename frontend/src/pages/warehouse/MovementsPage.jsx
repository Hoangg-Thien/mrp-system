import { useState } from 'react'
import { Button, DatePicker, Select, Space } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { api } from '../../api/client'
import { usePagedQuery } from '../../api/hooks'
import PageHeader from '../../components/PageHeader'
import PagedTable from '../../components/PagedTable'
import { MovementTypeTag } from '../../components/StatusTag'
import { dateTime, quantity } from '../../utils/format'
import { emptyText, movementTypeOptions } from '../../utils/statusMap'

const PAGE_SIZE = 20

/** W3 – Sổ kho (append-only). */
export default function MovementsPage() {
  const [filters, setFilters] = useState({})
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState({ sort: 'createdAt', direction: 'desc' })

  const query = usePagedQuery('stock-movements', '/api/stock-movements', {
    ...filters, ...sort, page, size: PAGE_SIZE,
  })

  const materialsQuery = useQuery({
    queryKey: ['materials-for-movements-filter'],
    queryFn: () => api.get('/api/materials', { size: 100 }),
  })

  const productsQuery = useQuery({
    queryKey: ['products-for-movements-filter'],
    queryFn: () => api.get('/api/products', { size: 100 }),
  })

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 70, sorter: true },
    { title: 'Loại', dataIndex: 'movementType', width: 140, render: (v) => <MovementTypeTag type={v} /> },
    { title: 'Số lượng', dataIndex: 'quantity', width: 110, render: (v) => quantity(v) },
    {
      title: 'Vật tư / SP',
      key: 'item',
      render: (_, r) => r.materialName
        ? `${r.materialCode || ''} ${r.materialName}`.trim()
        : (r.productName || '—'),
    },
    { title: 'ĐVT', dataIndex: 'unit', width: 70 },
    {
      title: 'Chứng từ nguồn',
      key: 'source',
      width: 170,
      render: (_, r) => r.sourceType ? `${r.sourceType} #${r.sourceId}` : '—',
    },
    { title: 'Người ghi', dataIndex: 'createdByUsername', width: 120, render: emptyText },
    { title: 'Thời gian', dataIndex: 'createdAt', sorter: true, width: 160, render: dateTime },
    { title: 'Ghi chú', dataIndex: 'note', ellipsis: true, render: emptyText },
  ].map((col) => (
    col.sorter
      ? { ...col, sortOrder: sort.sort === col.dataIndex ? (sort.direction === 'desc' ? 'descend' : 'ascend') : null }
      : col
  ))

  return (
    <>
      <PageHeader title="Sổ kho" subtitle="Lịch sử nhập / xuất / điều chỉnh – chỉ đọc, không sửa xóa" />
      <PagedTable
        filters={(
          <Space wrap>
            <Select
              allowClear
              style={{ width: 170 }}
              placeholder="Loại biến động"
              options={movementTypeOptions}
              value={filters.type}
              onChange={(v) => { setPage(0); setFilters((f) => ({ ...f, type: v })) }}
            />
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              style={{ width: 220 }}
              placeholder="Chọn nguyên vật liệu"
              value={filters.materialId}
              options={materialsQuery.data?.content?.map((m) => ({
                value: m.id,
                label: `[${m.code}] ${m.name}`,
              })) || []}
              onChange={(v) => { setPage(0); setFilters((f) => ({ ...f, materialId: v || undefined })) }}
            />
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              style={{ width: 200 }}
              placeholder="Chọn sản phẩm"
              value={filters.productId}
              options={productsQuery.data?.content?.map((p) => ({
                value: p.id,
                label: p.name,
              })) || []}
              onChange={(v) => { setPage(0); setFilters((f) => ({ ...f, productId: v || undefined })) }}
            />
            <DatePicker.RangePicker
              style={{ width: 260 }}
              value={filters.from && filters.to ? [dayjs(filters.from), dayjs(filters.to)] : null}
              onChange={(range) => {
                setPage(0)
                setFilters((f) => ({
                  ...f,
                  from: range?.[0]?.format('YYYY-MM-DD'),
                  to: range?.[1]?.format('YYYY-MM-DD'),
                }))
              }}
            />
            <Button icon={<ReloadOutlined />} onClick={() => { setFilters({}); setPage(0) }}>Đặt lại</Button>
          </Space>
        )}
        columns={columns}
        data={query.data?.content || []}
        loading={query.isFetching}
        page={page}
        size={PAGE_SIZE}
        total={query.data?.totalElements || 0}
        onPageChange={setPage}
        onTableChange={(_p, _f, sorter) => {
          const next = Array.isArray(sorter) ? sorter[0] : sorter
          setPage(0)
          setSort(next?.order
            ? { sort: next.field, direction: next.order === 'descend' ? 'desc' : 'asc' }
            : { sort: 'createdAt', direction: 'desc' })
        }}
      />
    </>
  )
}
