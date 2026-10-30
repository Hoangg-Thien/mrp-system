import { Alert, Card, Timeline } from 'antd'
import PageHeader from '../components/PageHeader'

/**
 * Màn hình chờ cho các phase backend chưa triển khai (P3–P9).
 * Layout/guard đã sẵn sàng; chỉ thêm màn hình khi API tương ứng hoàn tất.
 */
export default function PendingPage({ area, items }) {
  return (
    <>
      <PageHeader title={`Khu vực ${area}`} subtitle="Chức năng đang được tích hợp theo kế hoạch triển khai" />
      <Alert type="warning" showIcon className="inline-alert" title="Chưa có dữ liệu để hiển thị"
        description="Dữ liệu thực tế của phân hệ đang được đồng bộ và sẽ sẵn sàng khi hoàn tất quy trình thiết lập." />
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
