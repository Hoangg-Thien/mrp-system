import { useState } from 'react'
import { Button, Input, Modal, Popconfirm, Select, Space, Tooltip } from 'antd'
import { LockOutlined, PlusOutlined, ReloadOutlined, SwapOutlined, UnlockOutlined } from '@ant-design/icons'
import { usePagedQuery, useResourceActions } from '../../api/hooks'
import { useAuth } from '../../auth/context'
import PageHeader from '../../components/PageHeader'
import PagedTable from '../../components/PagedTable'
import FormModal from '../../components/FormModal'
import StatusTag, { RoleTag } from '../../components/StatusTag'
import { activeOptions, emptyText, roleOptions } from '../../utils/statusMap'
import { directionOptions, sortOptions, userFields } from './userFields'

const PAGE_SIZE = 10

/** W1 – Quản lý tài khoản: tạo, khóa/mở khóa, đổi vai trò, xóa (backend là nguồn sự thật). */
export default function UsersPage() {
  const { user: me } = useAuth()
  const [filters, setFilters] = useState({ sort: 'createdAt', direction: 'desc' })
  const [page, setPage] = useState(0)
  const [open, setOpen] = useState(false)
  const [roleTarget, setRoleTarget] = useState(null)
  const [roleValue, setRoleValue] = useState(undefined)

  const listQuery = usePagedQuery('users', '/api/admin/users', { ...filters, page, size: PAGE_SIZE })
  const actions = useResourceActions({ key: 'users', path: '/api/admin/users' })
  const change = (patch) => { setPage(0); setFilters((old) => ({ ...old, ...patch })) }

  const columns = [
    { title: 'Tên đăng nhập', dataIndex: 'username' },
    { title: 'Họ tên', dataIndex: 'fullName' },
    { title: 'Vai trò', dataIndex: 'role', width: 150, render: (value) => <RoleTag role={value} /> },
    { title: 'Email', dataIndex: 'email', render: emptyText },
    { title: 'Số điện thoại', dataIndex: 'phone', width: 140, render: emptyText },
    { title: 'Trạng thái', dataIndex: 'active', width: 150, render: (value) => <StatusTag active={value} /> },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      width: 320,
      render: (_, row) => {
        const isSelf = row.id === me.id
        const guard = (node) => (isSelf ? <Tooltip title="Không thể thao tác trên tài khoản đang đăng nhập">{node}</Tooltip> : node)
        return (
          <Space size={4}>
            {guard(<Button size="small" icon={<SwapOutlined />} disabled={isSelf}
              onClick={() => { setRoleTarget(row); setRoleValue(row.role) }}>Đổi vai trò</Button>)}
            {guard(<Button size="small" icon={row.active ? <LockOutlined /> : <UnlockOutlined />} disabled={isSelf}
              onClick={() => actions.runAction.mutate({ id: row.id, name: row.active ? 'lock' : 'unlock' })}>
              {row.active ? 'Khóa' : 'Mở khóa'}
            </Button>)}
            {guard(
              <Popconfirm title="Xóa tài khoản này?" okText="Xóa" cancelText="Hủy" okButtonProps={{ danger: true }} disabled={isSelf}
                onConfirm={() => actions.remove.mutateAsync(row.id).catch(() => undefined)}>
                <Button size="small" danger disabled={isSelf}>Xóa</Button>
              </Popconfirm>,
            )}
          </Space>
        )
      },
    },
  ]

  const submit = async (values) => {
    try {
      await actions.create.mutateAsync(values)
      setOpen(false)
    } catch {
      /* lỗi đã được thông báo trong useResourceActions */
    }
  }

  const changeRole = async () => {
    try {
      await actions.runAction.mutateAsync({ id: roleTarget.id, name: 'role', body: { role: roleValue } })
      setRoleTarget(null)
    } catch {
      /* lỗi đã được thông báo */
    }
  }

  return (
    <>
      <PageHeader title="Tài khoản" subtitle="Tạo và phân quyền STAFF / WAREHOUSE_MANAGER / ADMIN"
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>Tạo tài khoản</Button>} />
      <PagedTable
        filters={(
          <>
            <Input.Search allowClear style={{ width: 280 }} placeholder="Tìm username / họ tên / email…"
              onSearch={(value) => change({ keyword: value?.trim() || undefined })} />
            <Select allowClear style={{ width: 190 }} placeholder="Vai trò" options={roleOptions} value={filters.role}
              onChange={(value) => change({ role: value })} />
            <Select allowClear style={{ width: 200 }} placeholder="Trạng thái" options={activeOptions} value={filters.active}
              onChange={(value) => change({ active: value })} />
            <Select style={{ width: 180 }} options={sortOptions} value={filters.sort} onChange={(value) => change({ sort: value })} />
            <Select style={{ width: 140 }} options={directionOptions} value={filters.direction} onChange={(value) => change({ direction: value })} />
            <Button icon={<ReloadOutlined />} onClick={() => { setFilters({ sort: 'createdAt', direction: 'desc' }); setPage(0) }}>Đặt lại</Button>
          </>
        )}
        columns={columns}
        data={listQuery.data?.content || []}
        loading={listQuery.isFetching}
        page={page}
        size={PAGE_SIZE}
        total={listQuery.data?.totalElements || 0}
        onPageChange={setPage}
      />
      <FormModal title="Tạo tài khoản" open={open} fields={userFields} confirmLoading={actions.create.isPending}
        onCancel={() => setOpen(false)} onSubmit={submit} />
      <Modal title={`Đổi vai trò: ${roleTarget?.username || ''}`} open={!!roleTarget} onOk={changeRole} okText="Lưu" cancelText="Hủy"
        confirmLoading={actions.runAction.isPending} onCancel={() => setRoleTarget(null)}>
        <p className="muted">Backend sẽ tạo/xóa hồ sơ tương ứng và chặn nếu tài khoản đã phát sinh dữ liệu nghiệp vụ.</p>
        <Select style={{ width: '100%' }} options={roleOptions} value={roleValue} onChange={setRoleValue} />
      </Modal>
    </>
  )
}
