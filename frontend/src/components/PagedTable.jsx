import { Card, Pagination, Table } from 'antd'
import QueryError from './QueryError'

/**
 * Bảng phân trang dùng chung: `filters` là hàng điều kiện tìm kiếm, phân trang và
 * sắp xếp đều gửi lên backend (backend giữ whitelist sort).
 */
export default function PagedTable({ query, filters, columns, data, loading, page, size, total, onPageChange, onTableChange, rowKey = 'id' }) {
  return (
    <Card className="table-card">
      {filters && <div className="table-filters">{filters}</div>}
      {query?.isError ? <QueryError query={query} /> : <>
      <Table
        rowKey={rowKey}
        loading={loading}
        dataSource={data}
        columns={columns}
        pagination={false}
        onChange={onTableChange}
        locale={{ emptyText: 'Không có dữ liệu' }}
        scroll={{ x: 'max-content' }}
        size="small"
        bordered
      />
      <Pagination
        className="table-pagination"
        current={page + 1}
        pageSize={size}
        total={total}
        showSizeChanger={false}
        onChange={(value) => onPageChange(value - 1)}
        showTotal={(sum, range) => `${range[0]}–${range[1]} / ${sum} bản ghi`}
        size="small"
      />
      </>}
    </Card>
  )
}
