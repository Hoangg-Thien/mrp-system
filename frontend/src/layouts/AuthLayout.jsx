/** Khung màn hình đăng nhập (khu vực công khai). */
export default function AuthLayout({ children }) {
  return (
    <div className="auth-layout">
      <div className="auth-panel">
        <div className="brand-mark">MRP</div>
        <h1>Hệ thống quản lý sản xuất MRP</h1>
        <p>Quản trị tài khoản, nguyên vật liệu, kho và kế hoạch sản xuất.</p>
        <ul className="auth-points">
          <li>Phân quyền 3 vai trò: Quản trị viên, Quản lý kho, Nhân viên</li>
          <li>Nhập kho – BOM – Kế hoạch MRP – Sản xuất – Báo cáo</li>
          <li>Nghiệp vụ và tồn kho do máy chủ quyết định</li>
        </ul>
      </div>
      <div className="auth-form">{children}</div>
    </div>
  )
}
