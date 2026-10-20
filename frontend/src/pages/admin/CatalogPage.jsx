import { useMemo, useState } from 'react'
import { Button, Popconfirm, Space } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'
import { usePagedQuery, useResourceActions } from '../../api/hooks'
import PageHeader from '../../components/PageHeader'
import PagedTable from '../../components/PagedTable'
import FormModal from '../../components/FormModal'
import FilterControl from '../../components/FilterControl'
import StatusTag from '../../components/StatusTag'
import { resources } from './resources'

const PAGE_SIZE = 10

/** Màn hình CRUD danh mục dùng chung cho W2: danh mục, sản phẩm, NCC, nguyên vật liệu. */
export default function CatalogPage({ type }) {
  const resource = resources[type]
  const [filters, setFilters] = useState({})
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState(resource.defaultSort)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState(null)

  const listQuery = usePagedQuery(type, resource.path, { ...filters, ...sort, page, size: PAGE_SIZE })
  const categoriesQuery = useQuery({
    queryKey: ['category-options'],
    queryFn: ({ signal }) => api.get('/api/categories', { size: 100, sort: 'name', direction: 'asc' }, signal),
    enabled: type === 'products',
  })
  const actions = useResourceActions({ key: type, path: resource.path, invalidateKeys: [type, 'category-options'] })
  const categoryList = categoriesQuery.data?.content
  const categoryOptions = useMemo(() => (categoryList || []).map((item) => ({ value: item.id, label: item.name })), [categoryList])
  const categoryNames = useMemo(() => Object.fromEntries((categoryList || []).map((item) => [item.id, item.name])), [categoryList])

  const changeFilter = (name, value) => {
    setPage(0)
    setFilters((old) => ({ ...old, [name]: value }))
  }

  const columns = useMemo(() => {
    const base = resource.columns.map((column) => {
      if (column.dataIndex === 'categoryId') return { ...column, render: (value) => categoryNames[value] || `#${value}` }
      if (column.dataIndex === 'active') return { ...column, render: (value) => <StatusTag active={value} /> }
      if (column.sorter) {
        return { ...column, sortOrder: sort.sort === column.dataIndex ? (sort.direction === 'desc' ? 'descend' : 'ascend') : null }
      }
      return column
    })
    return [...base, {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      width: resource.hasActive ? 300 : 190,
      render: (_, row) => (
        <Space size={4}>
          <Button size="small" onClick={() => { setEditing(row); setOpen(true) }}>Sửa</Button>
          {resource.hasActive && (
            <Button size="small" onClick={() => actions.runAction.mutate({ id: row.id, name: row.active ? 'deactivate' : 'activate' })}>
              {row.active ? 'Vô hiệu hóa' : 'Kích hoạt'}
            </Button>
          )}
          <Popconfirm title="Xóa bản ghi này?" okText="Xóa" cancelText="Hủy" okButtonProps={{ danger: true }}
            onConfirm={() => actions.remove.mutateAsync(row.id).catch(() => undefined)}>
            <Button size="small" danger>Xóa</Button>
          </Popconfirm>
        </Space>
      ),
    }]
  }, [resource, categoryNames, sort, actions])

  const submit = async (values) => {
    try {
      if (editing) await actions.update.mutateAsync({ id: editing.id, body: values })
      else await actions.create.mutateAsync(values)
      setOpen(false)
      setEditing(null)
    } catch {
      /* lỗi đã được thông báo trong useResourceActions */
    }
  }

  return (
    <>
      <PageHeader title={resource.title} subtitle={resource.subtitle}
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditing(null); setOpen(true) }}>Thêm mới</Button>} />
      <PagedTable
        filters={(
          <>
            {resource.filters.map((filter) => (
              <FilterControl key={filter.name} filter={filter} value={filters[filter.name]} categoryOptions={categoryOptions}
                onChange={(value) => changeFilter(filter.name, value)} />
            ))}
            <Button icon={<ReloadOutlined />} onClick={() => { setFilters({}); setPage(0); setSort(resource.defaultSort) }}>Đặt lại</Button>
          </>
        )}
        columns={columns}
        data={listQuery.data?.content || []}
        loading={listQuery.isFetching}
        page={page}
        size={PAGE_SIZE}
        total={listQuery.data?.totalElements || 0}
        onPageChange={setPage}
        onTableChange={(_pagination, _filters, sorter) => {
          const next = Array.isArray(sorter) ? sorter[0] : sorter
          setPage(0)
          setSort(next?.order ? { sort: next.field, direction: next.order === 'descend' ? 'desc' : 'asc' } : resource.defaultSort)
        }}
      />
      <FormModal title={`${editing ? 'Sửa' : 'Thêm'} ${resource.singular}`} open={open} fields={resource.fields}
        options={{ categories: categoryOptions }} initialValues={editing || undefined}
        confirmLoading={actions.create.isPending || actions.update.isPending}
        onCancel={() => { setOpen(false); setEditing(null) }} onSubmit={submit} />
    </>
  )
}
