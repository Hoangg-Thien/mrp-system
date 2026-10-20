import { Alert, Card, Timeline } from 'antd'
import PageHeader from '../components/PageHeader'

/**
 * Màn hình chờ cho các phase backend chưa triển khai (P3–P9).
 * Layout/guard đã sẵn sàng; chỉ thêm màn hình khi API tương ứng hoàn tất.
 */
export default function PendingPage({ area, items }) {
  return (
    <>
      <PageHeader title={`Khu vực ${area}`} subtitle="Đang chờ backend hoàn thiện API tương ứng" />
      <Alert type="warning" showIcon className="inline-alert" message="Chưa có dữ liệu để hiển thị"
        description="Frontend không giả lập số liệu. Các màn hình dưới đây sẽ được xây dựng ngay khi backend triển khai xong phase liên quan." />
      <Card title="Lộ trình còn lại">
        <Timeline
          items={items.map((item) => ({
            color: item.phase <= 2 ? 'green' : 'gray',
            children: (
              <>
                <strong>{item.label}</strong>
                <div className="muted">{item.detail}</div>
              </>
            ),
          }))}
        />
      </Card>
    </>
  )
}
