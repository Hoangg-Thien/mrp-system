/** Tiêu đề trang + khu vực nút thao tác. */
export default function PageHeader({ title, subtitle, extra }) {
  return (
    <div className="page-header">
      <div>
        <h2 className="page-title">{title}</h2>
        {subtitle && <p className="page-subtitle">{subtitle}</p>}
      </div>
      {extra && <div className="page-extra">{extra}</div>}
    </div>
  )
}
