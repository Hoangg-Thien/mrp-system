import { useState } from 'react'
import { Button, Input, Modal, Select, Space, Tooltip } from 'antd'
import { LockOutlined, PlusOutlined, ReloadOutlined, SwapOutlined, UnlockOutlined } from '@ant-design/icons'
import { usePagedQuery, useResourceActions } from '../../api/hooks'
import { useAuth } from '../../auth/context'
import PageHeader from '../../components/PageHeader'
import PagedTable from '../../components/PagedTable'
import FormModal from '../../components/FormModal'
import StatusTag, { RoleTag } from '../../components/StatusTag'
import { useConfirm } from '../../utils/confirm'
import { activeOptions, emptyText, roleOptions } from '../../utils/statusMap'
import { dateTime } from '../../utils/format'
import { directionOptions, sortOptions, userFields } from './userFields'

const PAGE_SIZE = 10

/** W1 – Quản lý tài khoản: tạo, khóa/mở khóa, đổi vai trò (backend là nguồn sự thật). */
export default function UsersPage() {
  const { confirmAction } = useConfirm()
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
    { title: 'Tên đăng nhập', dataIndex: 'username', width: 140 },
    { title: 'Họ tên', dataIndex: 'fullName', width: 180 },
    { title: 'Vai trò', dataIndex: 'role', width: 150, render: (value) => <RoleTag role={value} /> },
    { title: 'Email', dataIndex: 'email', render: emptyText },
    { title: 'Số điện thoại', dataIndex: 'phone', width: 130, render: emptyText },
    { title: 'Trạng thái', dataIndex: 'active', width: 140, render: (value) => <StatusTag active={value} /> },
    { title: 'Ngày tạo', dataIndex: 'createdAt', width: 160, render: (value) => dateTime(value) },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      width: 210,
      render: (_, row) => {
        const isSelf = row.id === me.id
        const guard = (node) => (isSelf ? <Tooltip title="Không thể thao tác trên tài khoản đang đăng nhập">{node}</Tooltip> : node)
        return (
          <Space size={4}>
            {guard(
              <Button
                size="small"
                icon={<SwapOutlined />}
                disabled={isSelf}
                onClick={() => { setRoleTarget(row); setRoleValue(row.role) }}
              >
                Đổi vai trò
              </Button>
            )}
            {guard(
              <Button loading={actions.runAction.isPending}
                size="small"
                icon={row.active ? <LockOutlined /> : <UnlockOutlined />}
                disabled={(isSelf) || (actions.runAction.isPending)}
                danger={row.active}
                onClick={() =>
                  confirmAction({
                    title: row.active ? 'Xác nhận khóa tài khoản?' : 'Xác nhận mở khóa tài khoản?',
                    content: row.active
                      ? `Bạn có chắc chắn muốn khóa tài khoản "${row.username}"? Người dùng sẽ bị chặn đăng nhập vào hệ thống.`
                      : `Mở khóa tài khoản "${row.username}" để người dùng có thể đăng nhập lại bình thường?`,
                    okText: row.active ? 'Khóa tài khoản' : 'Mở khóa',
                    danger: row.active,
                    onOk: () => actions.runAction.mutateAsync({ id: row.id, name: row.active ? 'lock' : 'unlock' }),
                  })
                }
              >
                {row.active ? 'Khóa' : 'Mở khóa'}
              </Button>
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
      <PageHeader title="Tài khoản" subtitle="Tạo và phân quyền Quản trị viên / Quản lý kho / Nhân viên xưởng"
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>Tạo tài khoản</Button>} />
      <PagedTable query={listQuery}
        filters={(
          <>
            <Input.Search allowClear style={{ width: 260 }} placeholder="Tìm username / họ tên / email…"
              onSearch={(value) => change({ keyword: value?.trim() || undefined })} />
            <Select allowClear style={{ width: 170 }} placeholder="Vai trò" options={roleOptions} value={filters.role}
              onChange={(value) => change({ role: value })} />
            <Select allowClear style={{ width: 170 }} placeholder="Trạng thái" options={activeOptions} value={filters.active}
              onChange={(value) => change({ active: value })} />
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6, marginLeft: 'auto' }}>
              <span style={{ fontSize: 13, color: '#6b7280', whiteSpace: 'nowrap' }}>Sắp xếp:</span>
              <Select style={{ width: 160 }} options={sortOptions} value={filters.sort} onChange={(value) => change({ sort: value })} />
              <Select style={{ width: 120 }} options={directionOptions} value={filters.direction} onChange={(value) => change({ direction: value })} />
              <Button icon={<ReloadOutlined />} onClick={() => { setFilters({ sort: 'createdAt', direction: 'desc' }); setPage(0) }}>Đặt lại</Button>
            </div>
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
      <Modal cancelButtonProps={{ disabled: actions.runAction.isPending }} closable={!(actions.runAction.isPending)} mask={{ closable: !(actions.runAction.isPending) }} okButtonProps={{ disabled: actions.runAction.isPending }}
        title={`Đổi vai trò: ${roleTarget?.username || ''}`}
        open={!!roleTarget}
        onOk={changeRole}
        okText="Lưu"
        cancelText="Hủy"
        centered
        width={480}
        confirmLoading={actions.runAction.isPending}
        onCancel={() => setRoleTarget(null)}
      >
        <p className="muted" style={{ marginBottom: 12 }}>
          Hệ thống sẽ cập nhật quyền hạn tương ứng và chặn thao tác nếu tài khoản đã phát sinh dữ liệu liên quan.
        </p>
        <Select style={{ width: '100%' }} options={roleOptions} value={roleValue} onChange={setRoleValue} />
      </Modal>
    </>
  )
}
