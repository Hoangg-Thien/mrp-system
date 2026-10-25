import { useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Divider,
  Form,
  InputNumber,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
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

  const productsQuery = useQuery({
    queryKey: ['products-for-mrp'],
    queryFn: () => api.get('/api/products', { size: 200 }),
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
      <PageHeader
        title="Kế hoạch MRP (Material Requirements Planning)"
        subtitle="Tính toán nhu cầu sản xuất thành phẩm và dự toán thiếu hụt nguyên vật liệu theo định mức BOM"
      />

      <Card style={{ marginBottom: 20 }}>
        <Form form={form} layout="inline" onFinish={handleCalculate}>
          <Form.Item
            name="productId"
            label="Sản phẩm mục tiêu"
            rules={[{ required: true, message: 'Vui lòng chọn sản phẩm' }]}
            style={{ minWidth: 320 }}
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
            <InputNumber min={1} step={1} precision={0} placeholder="Số lượng" style={{ width: 140 }} />
          </Form.Item>

          <Form.Item>
            <Button
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
          style={{ marginBottom: 20 }}
          message="Không thể tính toán kế hoạch MRP"
          description={planQuery.error.message || 'Sản phẩm này có thể chưa có BOM active hoặc gặp lỗi tính toán.'}
        />
      )}

      {result && (
        <Space direction="vertical" size={20} style={{ width: '100%' }}>
          {/* 1. Phần Thành phẩm */}
          <Card title={`1. Phân tích Thành phẩm: ${selectedProduct?.name || ''}`}>
            <Row gutter={[16, 16]}>
              <Col xs={12} sm={8} md={4}>
                <Statistic title="Mục tiêu cần có" value={result.targetQuantity} suffix={selectedProduct?.unit} />
              </Col>
              <Col xs={12} sm={8} md={4}>
                <Statistic title="Tồn kho thực tế" value={quantity(result.fgOnHand, 0)} suffix={selectedProduct?.unit} />
              </Col>
              <Col xs={12} sm={8} md={4}>
                <Statistic title="Đã giữ cho đơn bán" value={quantity(result.fgReserved, 0)} suffix={selectedProduct?.unit} valueStyle={{ color: '#d97706' }} />
              </Col>
              <Col xs={12} sm={8} md={4}>
                <Statistic title="Đang sản xuất (PR)" value={quantity(result.fgIncoming, 0)} suffix={selectedProduct?.unit} valueStyle={{ color: '#2563eb' }} />
              </Col>
              <Col xs={12} sm={8} md={4}>
                <Statistic
                  title="Tồn khả dụng"
                  value={quantity(result.available, 0)}
                  suffix={selectedProduct?.unit}
                  valueStyle={{ color: result.available > 0 ? '#16653a' : '#4b5563', fontWeight: 600 }}
                />
              </Col>
              <Col xs={12} sm={8} md={4}>
                <Statistic
                  title="Cần sản xuất thêm"
                  value={result.toProduce}
                  suffix={selectedProduct?.unit}
                  valueStyle={{ color: result.toProduce > 0 ? '#dc2626' : '#16653a', fontWeight: 700 }}
                />
              </Col>
            </Row>

            <Divider style={{ margin: '16px 0 12px' }} />

            <Descriptions size="small" column={1}>
              <Descriptions.Item label="Công thức tính tồn khả dụng">
                <code>Khả dụng = Tồn thực ({quantity(result.fgOnHand, 0)}) - Đã giữ ({quantity(result.fgReserved, 0)}) + Đang SX ({quantity(result.fgIncoming, 0)}) = {quantity(result.available, 0)}</code>
              </Descriptions.Item>
              <Descriptions.Item label="Kết luận nhu cầu sản xuất">
                {result.toProduce > 0 ? (
                  <Tag color="volcano" style={{ fontSize: 13, padding: '4px 8px' }}>
                    Cần lập yêu cầu sản xuất {result.toProduce} {selectedProduct?.unit}
                  </Tag>
                ) : (
                  <Tag color="green" style={{ fontSize: 13, padding: '4px 8px' }}>
                    Tồn khả dụng đã đủ đáp ứng mục tiêu, không cần sản xuất thêm
                  </Tag>
                )}
              </Descriptions.Item>
            </Descriptions>
          </Card>

          {/* 2. Phần Nguyên vật liệu theo BOM */}
          <Card
            title="2. Nhu cầu Nguyên vật liệu theo công thức BOM"
            extra={
              hasShortage ? (
                <Tag icon={<ExclamationCircleOutlined />} color="error">
                  Phát hiện thiếu hụt nguyên vật liệu!
                </Tag>
              ) : (
                <Tag icon={<CheckCircleOutlined />} color="success">
                  Nguyên vật liệu đủ cho kế hoạch
                </Tag>
              )
            }
          >
            <Table
              size="middle"
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
                  render: (v) => <Typography.Text type={v > 0 ? 'success' : 'secondary'}>{quantity(v)}</Typography.Text>,
                },
                {
                  title: 'Thiếu hụt (Shortage)',
                  dataIndex: 'shortage',
                  width: 140,
                  render: (v) => {
                    const num = Number(v) || 0
                    return num > 0 ? (
                      <Tag color="red" style={{ fontWeight: 600 }}>
                        Thiếu: {quantity(num)}
                      </Tag>
                    ) : (
                      <Tag color="green">Đủ</Tag>
                    )
                  },
                },
              ]}
            />
          </Card>

          {/* 3. Hành động đề xuất */}
          <Card title="3. Hành động đề xuất từ hệ thống">
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              {result.toProduce > 0 && (
                <Alert
                  type="info"
                  showIcon
                  message={`Cần sản xuất thêm ${result.toProduce} ${selectedProduct?.unit || 'sản phẩm'}`}
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
                  message="Cần đặt hàng mua bổ sung nguyên vật liệu thiếu hụt"
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
                  message="Không cần hành động bổ sung"
                  description="Kho hiện tại đã có đủ sản phẩm khả dụng đáp ứng mục tiêu đề ra."
                />
              )}
            </Space>
          </Card>
        </Space>
      )}
    </>
  )
}
