import { Alert, Button } from 'antd'

export default function QueryError({ query }) {
  if (!query?.isError) return null
  return <Alert type="error" showIcon title="Không tải được dữ liệu"
    description={query.error?.message || 'Vui lòng thử lại.'}
    action={<Button onClick={() => query.refetch()} loading={query.isFetching} disabled={query.isFetching}>Thử lại</Button>} />
}
