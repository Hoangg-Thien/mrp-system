import QueryError from '../../components/QueryError'
import { useMemo, useState } from 'react'
import {
  Button,
  Card,
  Form,
  InputNumber,
  Radio,
  Select,
  Table,
  Typography,
  App,
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
import { groupStockTotals, stockReportCsv, stockReportDigits, topStockItems } from '../../utils/stockReport'
import { reportGranularityOptions } from '../../utils/statusMap'

const currentYear = new Date().getFullYear()
const currentMonth = new Date().getMonth() + 1
const currentQuarter = Math.ceil(currentMonth / 3)
const summaryMetrics = [
  { field: 'openingBalance', title: 'Tồn đầu kỳ' },
  { field: 'inQuantity', title: 'Nhập trong kỳ', color: 'var(--status-approved-text)' },
  { field: 'outQuantity', title: 'Xuất trong kỳ', color: 'var(--status-rejected-text)' },
  { field: 'adjustInQuantity', title: 'Điều chỉnh (+)', color: 'var(--status-progress-text)' },
  { field: 'adjustOutQuantity', title: 'Điều chỉnh (-)', color: 'var(--status-pending-text)' },
  { field: 'closingBalance', title: 'Tồn cuối kỳ', color: 'var(--status-approved-text)' },
]

export default function StockReportsPage() {
  const { message } = App.useApp()
  const [form] = Form.useForm()
  const [granularity, setGranularity] = useState('MONTH')
  const [chartUnit, setChartUnit] = useState()

  const [queryParams, setQueryParams] = useState({
    kind: 'MATERIAL',
    granularity: 'MONTH',
    year: currentYear,
    period: currentMonth,
  })

  const reportQuery = useQuery({
    queryKey: ['stock-report', queryParams],
    queryFn: ({ signal }) => api.get('/api/reports/stock', queryParams, signal),
    enabled: !!queryParams,
    staleTime: 0,
  })

  const handleSearch = (values) => {
    const params = {
      kind: values.kind,
      granularity: values.granularity,
      year: values.year,
      ...(values.granularity !== 'YEAR' ? { period: values.period } : {}),
    }
    if (params.kind === queryParams.kind && params.granularity === queryParams.granularity
        && params.year === queryParams.year && params.period === queryParams.period) {
      void reportQuery.refetch()
      return
    }
    setQueryParams(params)
  }

  const items = useMemo(() => reportQuery.data || [], [reportQuery.data])
  const kind = queryParams.kind
  const leadingColumnCount = kind === 'MATERIAL' ? 4 : 3
  const digits = stockReportDigits(kind)
  const unitTotals = useMemo(() => groupStockTotals(items, kind), [items, kind])
  const selectedChartUnit = unitTotals.some((group) => group.unit === chartUnit)
    ? chartUnit : unitTotals[0]?.unit

  const chartData = useMemo(() => {
    return topStockItems(items, selectedChartUnit).map((item) => ({
      name: item.name?.length > 14 ? `${item.name.substring(0, 12)}...` : item.name,
      fullName: item.name,
      'Tồn đầu': Number(item.openingBalance) || 0,
      'Nhập kho': Number(item.inQuantity) || 0,
      'Xuất kho': Number(item.outQuantity) || 0,
      'Tồn cuối': Number(item.closingBalance) || 0,
    }))
  }, [items, selectedChartUnit])

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    ...(kind === 'MATERIAL' ? [{ title: 'Mã', dataIndex: 'code', width: 100, render: (v) => v || '—' }] : []),
    { title: 'Tên đối tượng', dataIndex: 'name' },
    { title: 'ĐVT', dataIndex: 'unit', width: 70 },
    {
      title: 'Tồn đầu kỳ',
      dataIndex: 'openingBalance',
      width: 120,
      render: (v) => quantity(v, digits),
    },
    {
      title: 'Nhập trong kỳ',
      dataIndex: 'inQuantity',
      width: 130,
      render: (v) => (
        <Typography.Text type={v > 0 ? 'success' : undefined} strong={v > 0}>
          +{quantity(v, digits)}
        </Typography.Text>
      ),
    },
    {
      title: 'Xuất trong kỳ',
      dataIndex: 'outQuantity',
      width: 130,
      render: (v) => (
        <Typography.Text type={v > 0 ? 'danger' : undefined} strong={v > 0}>
          -{quantity(v, digits)}
        </Typography.Text>
      ),
    },
    {
      title: 'Đ/c tăng (+)',
      dataIndex: 'adjustInQuantity',
      width: 110,
      render: (v) => quantity(v, digits),
    },
    {
      title: 'Đ/c giảm (-)',
      dataIndex: 'adjustOutQuantity',
      width: 110,
      render: (v) => quantity(v, digits),
    },
    {
      title: 'Tồn cuối kỳ',
      dataIndex: 'closingBalance',
      width: 130,
      render: (v) => (
        <Typography.Text strong style={{ color: 'var(--status-approved-text)' }}>
          {quantity(v, digits)}
        </Typography.Text>
      ),
    },
  ]

  const handleExportCsv = () => {
    if (!items.length) {
      message.info('Không có dữ liệu để xuất file')
      return
    }
    const csvContent = stockReportCsv(items, kind)
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
            disabled={reportQuery.isError || reportQuery.isFetching || !items.length}
          >
            Xuất file CSV
          </Button>
        )}
      />

      {/* Bộ lọc kỳ báo cáo */}
      <Card className="stock-report-filters" style={{ marginBottom: 14 }}>
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
            <Radio.Group>
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
            <Button disabled={reportQuery.isFetching}
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

      {reportQuery.isError ? <QueryError query={reportQuery} /> : <>
      {unitTotals.length > 0 && (
        <Card size="small" title="Tổng số lượng theo đơn vị" className="stock-report-summary">
          <div className="stock-report-summary-scroll" role="region" aria-label="Tổng số lượng theo đơn vị" tabIndex={0}>
            <table>
              <thead>
                <tr>
                  <th scope="col">Đơn vị</th>
                  {summaryMetrics.map((metric) => <th scope="col" key={metric.field}>{metric.title}</th>)}
                </tr>
              </thead>
              <tbody>
                {unitTotals.map((group) => (
                  <tr key={group.unit}>
                    <th scope="row">{group.unit}</th>
                    {summaryMetrics.map((metric) => (
                      <td key={metric.field} data-field={metric.field} style={{ color: metric.color }}>
                        {quantity(group[metric.field], digits)}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* Biểu đồ so sánh trực quan */}
      {chartData.length > 0 && (
        <Card
          size="small"
          className="stock-report-chart"
          title={`Top 10 theo tồn cuối kỳ (${selectedChartUnit})`}
          extra={
            <div className="stock-report-chart-control">
              <label htmlFor="stock-chart-unit">Đơn vị biểu đồ</label>
              <Select
                id="stock-chart-unit"
                size="small"
                style={{ minWidth: 90 }}
                value={selectedChartUnit}
                onChange={setChartUnit}
                options={unitTotals.map((group) => ({ value: group.unit, label: group.unit }))}
              />
            </div>
          }
        >
          <div style={{ width: '100%', height: 280 }}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData} margin={{ top: 8, right: 12, left: 0, bottom: 16 }}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" angle={-15} textAnchor="end" interval="preserveStartEnd" minTickGap={8} height={50} tick={{ fontSize: 12 }} />
                <YAxis allowDecimals={kind !== 'PRODUCT'} tick={{ fontSize: 12 }} tickFormatter={(value) => quantity(value, digits)} />
                <Tooltip
                  labelFormatter={(label, payload) => payload?.[0]?.payload.fullName || label}
                  formatter={(value) => `${quantity(value, digits)} ${selectedChartUnit}`}
                />
                <Legend verticalAlign="top" height={48} wrapperStyle={{ fontSize: 12, lineHeight: '18px' }} />
                <Bar dataKey="Tồn đầu" fill="#94a3b8" isAnimationActive={false} />
                <Bar dataKey="Nhập kho" fill="#166534" isAnimationActive={false} />
                <Bar dataKey="Xuất kho" fill="#b91c1c" isAnimationActive={false} />
                <Bar dataKey="Tồn cuối" fill="#1d4ed8" isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
      )}

      {/* Bảng dữ liệu chi tiết */}
      <Card className="stock-report-detail" title={`Bảng số liệu chi tiết – ${kind === 'MATERIAL' ? 'Nguyên vật liệu' : 'Thành phẩm'}`}>
        <Table
          size="middle"
          rowKey="id"
          loading={reportQuery.isFetching}
          dataSource={items}
          columns={columns}
          pagination={{ defaultPageSize: 10, showSizeChanger: true }}
          locale={{ emptyText: 'Không có biến động nào trong kỳ được chọn' }}
          scroll={{ x: 'max-content' }}
          summary={() => unitTotals.map((group) => (
            <Table.Summary.Row key={group.unit} style={{ background: '#f9fafb', fontWeight: 600 }}>
              <Table.Summary.Cell index={0} colSpan={leadingColumnCount} style={{ textAlign: 'right' }}>
                Tổng ({group.unit}):
              </Table.Summary.Cell>
              <Table.Summary.Cell index={leadingColumnCount}>
                {quantity(group.openingBalance, digits)}
              </Table.Summary.Cell>
              <Table.Summary.Cell index={leadingColumnCount + 1} style={{ color: 'var(--status-approved-text)' }}>
                +{quantity(group.inQuantity, digits)}
              </Table.Summary.Cell>
              <Table.Summary.Cell index={leadingColumnCount + 2} style={{ color: 'var(--status-rejected-text)' }}>
                -{quantity(group.outQuantity, digits)}
              </Table.Summary.Cell>
              <Table.Summary.Cell index={leadingColumnCount + 3}>
                {quantity(group.adjustInQuantity, digits)}
              </Table.Summary.Cell>
              <Table.Summary.Cell index={leadingColumnCount + 4}>
                {quantity(group.adjustOutQuantity, digits)}
              </Table.Summary.Cell>
              <Table.Summary.Cell index={leadingColumnCount + 5} style={{ color: 'var(--status-approved-text)', fontWeight: 700 }}>
                {quantity(group.closingBalance, digits)}
              </Table.Summary.Cell>
            </Table.Summary.Row>
          ))}
        />
      </Card>
      </>}
    </>
  )
}
