/** Khung màn hình đăng nhập (khu vực công khai). */
export default function AuthLayout({ children }) {
  return (
    <div className="auth-layout">
      <div className="auth-panel">
        <div className="brand-mark">MRP</div>
        <h1>Hệ thống Quản lý Kế hoạch Sản xuất &amp; Kho vận</h1>
        <p>Nền tảng quản trị tập trung phục vụ hoạch định nguồn lực, quản lý định mức nguyên vật liệu và điều hành chuỗi kho vận theo thời gian thực.</p>
        <ul className="auth-points">
          <li>Phân quyền nghiệp vụ: Quản trị hệ thống, Quản lý kho, Nhân viên xưởng</li>
          <li>Quy trình khép kín: Nhập kho &rarr; Công thức BOM &rarr; Kế hoạch MRP &rarr; Sản xuất &rarr; Báo cáo</li>
          <li>Kiểm soát tồn kho chính xác, ghi nhận nhật ký biến động kho minh bạch</li>
        </ul>
      </div>
      <div className="auth-form">{children}</div>
    </div>
  )
}
