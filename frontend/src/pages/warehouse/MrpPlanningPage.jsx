import { useAllPages } from '../../api/useAllPages'
import QueryError from '../../components/QueryError'
import { useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Divider,
  Form,
  InputNumber,
  Select,
  Statistic,
  Table,
  Typography,
} from 'antd'
import {
  CalculatorOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  PlusOutlined,
  ShoppingCartOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api } from '../../api/client'
import PageHeader from '../../components/PageHeader'
import { quantity } from '../../utils/format'

export default function MrpPlanningPage() {
  const navigate = useNavigate()
  const [form] = Form.useForm()
  const [planParams, setPlanParams] = useState(null)

  const productsQuery = useAllPages({
    queryKey: ['products-for-mrp'],
    path: '/api/products',
    params: {  },
  })

  const planQuery = useQuery({
    queryKey: ['mrp-plan-result', planParams],
    queryFn: () => api.get('/api/mrp/plan', planParams),
    enabled: !!planParams,
    retry: false,
  })

  const handleCalculate = (values) => {
    setPlanParams({
      productId: values.productId,
      quantity: values.quantity,
    })
  }

  const selectedProduct = productsQuery.data?.content?.find((p) => p.id === planParams?.productId)
  const result = planQuery.data
  const hasShortage = result?.materials?.some((m) => Number(m.shortage) > 0)

  return (
    <>
      <QueryError query={productsQuery} />
      <PageHeader
        title="Kế hoạch MRP (Material Requirements Planning)"
        subtitle="Tính toán nhu cầu sản xuất thành phẩm và dự toán thiếu hụt nguyên vật liệu theo định mức BOM"
      />

      <Card className="mrp-filter-card">
        <Form form={form} layout="inline" onFinish={handleCalculate}>
          <Form.Item
            name="productId"
            label="Sản phẩm mục tiêu"
            rules={[{ required: true, message: 'Vui lòng chọn sản phẩm' }]}
            className="mrp-select-product"
          >
            <Select
              showSearch
              placeholder="Chọn sản phẩm"
              optionFilterProp="label"
              options={productsQuery.data?.content?.map((p) => ({
                value: p.id,
                label: `${p.name} (${p.unit})`,
              })) || []}
            />
          </Form.Item>

          <Form.Item
            name="quantity"
            label="Số lượng muốn có sẵn"
            rules={[
              { required: true, message: 'Nhập số lượng mục tiêu' },
              { type: 'number', min: 1, message: 'Tối thiểu 1' },
            ]}
          >
            <InputNumber
              min={1}
              step={1}
              precision={0}
              placeholder="Số lượng"
              className="mrp-input-qty"
            />
          </Form.Item>

          <Form.Item>
            <Button disabled={planQuery.isFetching}
              type="primary"
              htmlType="submit"
              icon={<CalculatorOutlined />}
              loading={planQuery.isFetching}
            >
              Tính toán MRP
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {planQuery.isError && (
        <Alert
          type="error"
          showIcon
          className="mrp-alert"
          title="Không thể tính toán kế hoạch MRP"
          description={planQuery.error.message || 'Sản phẩm này có thể chưa có BOM active hoặc gặp lỗi tính toán.'}
        />
      )}

      {result && (
        <div className="mrp-results-container">
          {/* 1. Phần Thành phẩm */}
          <Card title={`1. Phân tích Thành phẩm: ${selectedProduct?.name || ''}`}>
            <div className="mrp-kpi-grid">
              <div className="mrp-kpi-cell kpi-val-neutral">
                <Statistic
                  title="Mục tiêu cần có"
                  value={result.targetQuantity}
                  suffix={selectedProduct?.unit}
                />
              </div>
              <div className="mrp-kpi-cell kpi-val-neutral">
                <Statistic
                  title="Tồn kho thực tế"
                  value={quantity(result.fgOnHand, 0)}
                  suffix={selectedProduct?.unit}
                />
              </div>
              <div className="mrp-kpi-cell kpi-val-warning">
                <Statistic
                  title="Đã giữ cho đơn bán"
                  value={quantity(result.fgReserved, 0)}
                  suffix={selectedProduct?.unit}
                />
              </div>
              <div className="mrp-kpi-cell kpi-val-progress">
                <Statistic
                  title="Đang sản xuất (PR)"
                  value={quantity(result.fgIncoming, 0)}
                  suffix={selectedProduct?.unit}
                />
              </div>
              <div className={`mrp-kpi-cell ${result.available > 0 ? 'kpi-val-success' : 'kpi-val-neutral'}`}>
                <Statistic
                  title="Tồn khả dụng"
                  value={quantity(result.available, 0)}
                  suffix={selectedProduct?.unit}
                />
              </div>
              <div className={`mrp-kpi-cell ${result.toProduce > 0 ? 'kpi-val-danger' : 'kpi-val-success'}`}>
                <Statistic
                  title="Cần sản xuất thêm"
                  value={result.toProduce}
                  suffix={selectedProduct?.unit}
                />
              </div>
            </div>

            <Divider className="mrp-divider" />

            <Descriptions size="small" column={1}>
              <Descriptions.Item label="Công thức tính tồn khả dụng">
                <div className="mrp-formula-box">
                  Khả dụng = Tồn thực ({quantity(result.fgOnHand, 0)}) - Đã giữ ({quantity(result.fgReserved, 0)}) + Đang SX ({quantity(result.fgIncoming, 0)}) = {quantity(result.available, 0)}
                </div>
              </Descriptions.Item>
              <Descriptions.Item label="Kết luận nhu cầu sản xuất">
                <span className={result.toProduce > 0 ? 'erp-tag erp-tag-rejected' : 'erp-tag erp-tag-approved'}>
                  {result.toProduce > 0
                    ? `Cần lập yêu cầu sản xuất ${result.toProduce} ${selectedProduct?.unit || ''}`
                    : 'Tồn khả dụng đã đủ đáp ứng mục tiêu, không cần sản xuất thêm'}
                </span>
              </Descriptions.Item>
            </Descriptions>
          </Card>

          {/* 2. Phần Nguyên vật liệu theo BOM */}
          <Card
            title="2. Nhu cầu Nguyên vật liệu theo công thức BOM"
            extra={
              hasShortage ? (
                <span className="erp-tag erp-tag-rejected">
                  <ExclamationCircleOutlined /> Phát hiện thiếu hụt nguyên vật liệu!
                </span>
              ) : (
                <span className="erp-tag erp-tag-approved">
                  <CheckCircleOutlined /> Nguyên vật liệu đủ cho kế hoạch
                </span>
              )
            }
          >
            <Table
              size="small"
              bordered
              rowKey="materialId"
              pagination={false}
              dataSource={result.materials || []}
              columns={[
                { title: 'Mã NVL', dataIndex: 'materialCode', width: 100 },
                { title: 'Tên nguyên vật liệu', dataIndex: 'materialName' },
                { title: 'ĐVT', dataIndex: 'unit', width: 70 },
                {
                  title: 'Định mức BOM',
                  dataIndex: 'bomQuantity',
                  width: 120,
                  render: (v) => `${quantity(v)} / ${selectedProduct?.unit || 'SP'}`,
                },
                {
                  title: 'Tổng nhu cầu thô (Gross)',
                  dataIndex: 'gross',
                  width: 140,
                  render: (v) => <Typography.Text strong>{quantity(v)}</Typography.Text>,
                },
                { title: 'Tồn kho (On Hand)', dataIndex: 'onHand', width: 120, render: (v) => quantity(v) },
                { title: 'Đã cam kết khác', dataIndex: 'committed', width: 120, render: (v) => quantity(v) },
                {
                  title: 'Dùng được (Usable)',
                  dataIndex: 'usable',
                  width: 120,
                  render: (v) => quantity(v),
                },
                {
                  title: 'Thiếu hụt (Shortage)',
                  dataIndex: 'shortage',
                  width: 140,
                  render: (v) => {
                    const num = Number(v) || 0
                    return num > 0 ? (
                      <span className="erp-tag erp-tag-rejected">
                        Thiếu: {quantity(num)}
                      </span>
                    ) : (
                      <span className="erp-tag erp-tag-approved">Đủ</span>
                    )
                  },
                },
              ]}
            />
          </Card>

          {/* 3. Hành động đề xuất từ hệ thống */}
          <Card title="3. Hành động đề xuất từ hệ thống">
            <div className="mrp-action-stack">
              {result.toProduce > 0 && (
                <Alert
                  type="info"
                  showIcon
                  title={`Cần sản xuất thêm ${result.toProduce} ${selectedProduct?.unit || 'sản phẩm'}`}
                  description="Bấm nút bên dưới để chuyển sang màn hình Tạo yêu cầu sản xuất cho nhân viên."
                  action={
                    <Button
                      type="primary"
                      icon={<PlusOutlined />}
                      onClick={() => navigate('/warehouse/production-requests', { state: { prefillProductId: result.productId, prefillQuantity: result.toProduce } })}
                    >
                      Tạo yêu cầu sản xuất
                    </Button>
                  }
                />
              )}

              {hasShortage && (
                <Alert
                  type="warning"
                  showIcon
                  title="Cần đặt hàng mua bổ sung nguyên vật liệu thiếu hụt"
                  description="Một số nguyên vật liệu trong kho không đủ để sản xuất sản lượng này. Bạn nên lập phiếu nhập kho NVL từ nhà cung cấp."
                  action={
                    <Button
                      icon={<ShoppingCartOutlined />}
                      onClick={() => navigate('/warehouse/receipts')}
                    >
                      Lập phiếu nhập kho NVL
                    </Button>
                  }
                />
              )}

              {result.toProduce === 0 && !hasShortage && (
                <Alert
                  type="success"
                  showIcon
                  title="Không cần hành động bổ sung"
                  description="Kho hiện tại đã có đủ sản phẩm khả dụng đáp ứng mục tiêu đề ra."
                />
              )}
            </div>
          </Card>
        </div>
      )}
    </>
  )
}
