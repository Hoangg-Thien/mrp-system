import { useMemo, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Form,
  InputNumber,
  Radio,
  Row,
  Select,
  Statistic,
  Table,
  Typography,
  message,
} from 'antd'
import { BarChartOutlined, DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { api } from '../../api/client'
import PageHeader from '../../components/PageHeader'
import { quantity } from '../../utils/format'
import { reportGranularityOptions } from '../../utils/statusMap'

const currentYear = new Date().getFullYear()
const currentMonth = new Date().getMonth() + 1
const currentQuarter = Math.ceil(currentMonth / 3)

export default function StockReportsPage() {
  const [form] = Form.useForm()
  const [granularity, setGranularity] = useState('MONTH')
  const [kind, setKind] = useState('MATERIAL')

  const [queryParams, setQueryParams] = useState({
    kind: 'MATERIAL',
    granularity: 'MONTH',
    year: currentYear,
    period: currentMonth,
  })

  const reportQuery = useQuery({
    queryKey: ['stock-report', queryParams],
    queryFn: () => api.get('/api/reports/stock', queryParams),
    enabled: !!queryParams,
  })

  const handleSearch = (values) => {
    const params = {
      kind: values.kind,
      granularity: values.granularity,
      year: values.year,
      ...(values.granularity !== 'YEAR' ? { period: values.period } : {}),
    }
    setQueryParams(params)
  }

  const items = useMemo(() => reportQuery.data || [], [reportQuery.data])

  // Quick summary metrics
  const totals = useMemo(() => {
    return items.reduce(
      (acc, item) => ({
        opening: acc.opening + (Number(item.openingBalance) || 0),
        inQty: acc.inQty + (Number(item.inQuantity) || 0),
        outQty: acc.outQty + (Number(item.outQuantity) || 0),
        adjIn: acc.adjIn + (Number(item.adjustInQuantity) || 0),
        adjOut: acc.adjOut + (Number(item.adjustOutQuantity) || 0),
        closing: acc.closing + (Number(item.closingBalance) || 0),
      }),
      { opening: 0, inQty: 0, outQty: 0, adjIn: 0, adjOut: 0, closing: 0 },
    )
  }, [items])

  // Chart data: Top items by activity
  const chartData = useMemo(() => {
    return items.slice(0, 10).map((item) => ({
      name: item.name?.length > 14 ? `${item.name.substring(0, 12)}...` : item.name,
      'Tồn đầu': Number(item.openingBalance) || 0,
      'Nhập kho': Number(item.inQuantity) || 0,
      'Xuất kho': Number(item.outQuantity) || 0,
      'Tồn cuối': Number(item.closingBalance) || 0,
    }))
  }, [items])

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: 'Mã', dataIndex: 'code', width: 100, render: (v) => v || '—' },
    { title: 'Tên đối tượng', dataIndex: 'name' },
    { title: 'ĐVT', dataIndex: 'unit', width: 70 },
    {
      title: 'Tồn đầu kỳ',
      dataIndex: 'openingBalance',
      width: 120,
      render: (v) => quantity(v, kind === 'PRODUCT' ? 0 : 3),
    },
    {
      title: 'Nhập trong kỳ',
      dataIndex: 'inQuantity',
      width: 130,
      render: (v) => (
        <Typography.Text type={v > 0 ? 'success' : undefined} strong={v > 0}>
          +{quantity(v, kind === 'PRODUCT' ? 0 : 3)}
        </Typography.Text>
      ),
    },
    {
      title: 'Xuất trong kỳ',
      dataIndex: 'outQuantity',
      width: 130,
      render: (v) => (
        <Typography.Text type={v > 0 ? 'danger' : undefined} strong={v > 0}>
          -{quantity(v, kind === 'PRODUCT' ? 0 : 3)}
        </Typography.Text>
      ),
    },
    {
      title: 'Đ/c tăng (+)',
      dataIndex: 'adjustInQuantity',
      width: 110,
      render: (v) => quantity(v, kind === 'PRODUCT' ? 0 : 3),
    },
    {
      title: 'Đ/c giảm (-)',
      dataIndex: 'adjustOutQuantity',
      width: 110,
      render: (v) => quantity(v, kind === 'PRODUCT' ? 0 : 3),
    },
    {
      title: 'Tồn cuối kỳ',
      dataIndex: 'closingBalance',
      width: 130,
      render: (v) => (
        <Typography.Text strong style={{ color: '#16653a' }}>
          {quantity(v, kind === 'PRODUCT' ? 0 : 3)}
        </Typography.Text>
      ),
    },
  ]

  const handleExportCsv = () => {
    if (!items.length) {
      message.info('Không có dữ liệu để xuất file')
      return
    }
    const headers = ['ID', 'Ma', 'Ten', 'DVT', 'Ton_dau_ky', 'Nhap_trong_ky', 'Xuat_trong_ky', 'Dieu_chinh_tang', 'Dieu_chinh_giam', 'Ton_cuoi_ky']
    const rows = items.map((it) => [
      it.id,
      `"${it.code || ''}"`,
      `"${it.name || ''}"`,
      `"${it.unit || ''}"`,
      it.openingBalance ?? 0,
      it.inQuantity ?? 0,
      it.outQuantity ?? 0,
      it.adjustInQuantity ?? 0,
      it.adjustOutQuantity ?? 0,
      it.closingBalance ?? 0,
    ])
    const csvContent = '\uFEFF' + [headers.join(','), ...rows.map((r) => r.join(','))].join('\n')
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `Bao_cao_ton_kho_${queryParams.kind}_${queryParams.granularity}_${queryParams.year}_${queryParams.period || ''}.csv`
    link.click()
    URL.revokeObjectURL(url)
    message.success('Đã tải xuống file báo cáo CSV thành công')
  }

  return (
    <>
      <PageHeader
        title="Báo cáo xuất – nhập – tồn kho theo kỳ"
        subtitle="Thống kê biến động xuất nhập tồn chính xác theo tháng, quý hoặc năm"
        extra={(
          <Button
            icon={<DownloadOutlined />}
            onClick={handleExportCsv}
            disabled={!items.length}
          >
            Xuất file CSV
          </Button>
        )}
      />

      {/* Bộ lọc kỳ báo cáo */}
      <Card style={{ marginBottom: 20 }}>
        <Form
          form={form}
          layout="inline"
          initialValues={{
            kind: 'MATERIAL',
            granularity: 'MONTH',
            year: currentYear,
            period: currentMonth,
          }}
          onFinish={handleSearch}
        >
          <Form.Item name="kind" label="Đối tượng">
            <Radio.Group onChange={(e) => setKind(e.target.value)}>
              <Radio.Button value="MATERIAL">Nguyên vật liệu</Radio.Button>
              <Radio.Button value="PRODUCT">Thành phẩm</Radio.Button>
            </Radio.Group>
          </Form.Item>

          <Form.Item name="granularity" label="Kỳ báo cáo">
            <Select
              style={{ width: 140 }}
              options={reportGranularityOptions}
              onChange={(v) => {
                setGranularity(v)
                if (v === 'MONTH') form.setFieldValue('period', currentMonth)
                if (v === 'QUARTER') form.setFieldValue('period', currentQuarter)
              }}
            />
          </Form.Item>

          <Form.Item name="year" label="Năm" rules={[{ required: true }]}>
            <InputNumber min={2000} max={2100} style={{ width: 100 }} />
          </Form.Item>

          {granularity === 'MONTH' && (
            <Form.Item name="period" label="Tháng" rules={[{ required: true }]}>
              <Select
                style={{ width: 110 }}
                options={Array.from({ length: 12 }, (_, i) => ({
                  value: i + 1,
                  label: `Tháng ${i + 1}`,
                }))}
              />
            </Form.Item>
          )}

          {granularity === 'QUARTER' && (
            <Form.Item name="period" label="Quý" rules={[{ required: true }]}>
              <Select
                style={{ width: 110 }}
                options={[
                  { value: 1, label: 'Quý 1' },
                  { value: 2, label: 'Quý 2' },
                  { value: 3, label: 'Quý 3' },
                  { value: 4, label: 'Quý 4' },
                ]}
              />
            </Form.Item>
          )}

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              icon={<BarChartOutlined />}
              loading={reportQuery.isFetching}
            >
              Xem báo cáo
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {/* Thẻ tóm tắt số liệu */}
      <Row gutter={[16, 16]} style={{ marginBottom: 20 }}>
        <Col xs={12} sm={8} lg={4}>
          <Card size="small">
            <Statistic
              title="Tổng tồn đầu kỳ"
              value={quantity(totals.opening, kind === 'PRODUCT' ? 0 : 2)}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <Card size="small">
            <Statistic
              title="Tổng nhập trong kỳ"
              value={quantity(totals.inQty, kind === 'PRODUCT' ? 0 : 2)}
              valueStyle={{ color: '#16653a' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <Card size="small">
            <Statistic
              title="Tổng xuất trong kỳ"
              value={quantity(totals.outQty, kind === 'PRODUCT' ? 0 : 2)}
              valueStyle={{ color: '#dc2626' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <Card size="small">
            <Statistic
              title="Điều chỉnh (+)"
              value={quantity(totals.adjIn, kind === 'PRODUCT' ? 0 : 2)}
              valueStyle={{ color: '#0891b2' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <Card size="small">
            <Statistic
              title="Điều chỉnh (-)"
              value={quantity(totals.adjOut, kind === 'PRODUCT' ? 0 : 2)}
              valueStyle={{ color: '#c026d3' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <Card size="small">
            <Statistic
              title="Tổng tồn cuối kỳ"
              value={quantity(totals.closing, kind === 'PRODUCT' ? 0 : 2)}
              valueStyle={{ color: '#047857', fontWeight: 700 }}
            />
          </Card>
        </Col>
      </Row>

      {/* Biểu đồ so sánh trực quan */}
      {chartData.length > 0 && (
        <Card title="Biểu đồ so sánh Tồn đầu, Nhập, Xuất, Tồn cuối (Top 10 mặt hàng)" style={{ marginBottom: 20 }}>
          <div style={{ width: '100%', height: 320 }}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData} margin={{ top: 20, right: 30, left: 20, bottom: 25 }}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataIndex="name" angle={-15} textAnchor="end" interval={0} height={50} />
                <YAxis />
                <Tooltip />
                <Legend verticalAlign="top" height={36} />
                <Bar dataKey="Tồn đầu" fill="#9ca3af" />
                <Bar dataKey="Nhập kho" fill="#16a34a" />
                <Bar dataKey="Xuất kho" fill="#ef4444" />
                <Bar dataKey="Tồn cuối" fill="#0284c7" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
      )}

      {/* Bảng dữ liệu chi tiết */}
      <Card title={`Bảng số liệu chi tiết – ${kind === 'MATERIAL' ? 'Nguyên vật liệu' : 'Thành phẩm'}`}>
        <Table
          size="middle"
          rowKey="id"
          loading={reportQuery.isFetching}
          dataSource={items}
          columns={columns}
          pagination={{ pageSize: 20, showSizeChanger: true }}
          locale={{ emptyText: 'Không có biến động nào trong kỳ được chọn' }}
          scroll={{ x: 'max-content' }}
          summary={() => (
            <Table.Summary.Row style={{ background: '#f9fafb', fontWeight: 600 }}>
              <Table.Summary.Cell index={0} colSpan={4} style={{ textAlign: 'right' }}>
                Tổng cộng toàn kho:
              </Table.Summary.Cell>
              <Table.Summary.Cell index={1}>
                {quantity(totals.opening, kind === 'PRODUCT' ? 0 : 2)}
              </Table.Summary.Cell>
              <Table.Summary.Cell index={2} style={{ color: '#16653a' }}>
                +{quantity(totals.inQty, kind === 'PRODUCT' ? 0 : 2)}
              </Table.Summary.Cell>
              <Table.Summary.Cell index={3} style={{ color: '#dc2626' }}>
                -{quantity(totals.outQty, kind === 'PRODUCT' ? 0 : 2)}
              </Table.Summary.Cell>
              <Table.Summary.Cell index={4}>
                {quantity(totals.adjIn, kind === 'PRODUCT' ? 0 : 2)}
              </Table.Summary.Cell>
              <Table.Summary.Cell index={5}>
                {quantity(totals.adjOut, kind === 'PRODUCT' ? 0 : 2)}
              </Table.Summary.Cell>
              <Table.Summary.Cell index={6} style={{ color: '#047857', fontWeight: 700 }}>
                {quantity(totals.closing, kind === 'PRODUCT' ? 0 : 2)}
              </Table.Summary.Cell>
            </Table.Summary.Row>
          )}
        />
      </Card>
    </>
  )
}
