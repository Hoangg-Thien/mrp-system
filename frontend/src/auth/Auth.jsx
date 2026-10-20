import { useEffect, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Space, Spin } from 'antd'
import { request, session } from '../api/client'
import { AuthContext } from './context'
export function AuthProvider({ children }) {
  const client = useQueryClient()
  const [token, setToken] = useState(session.get)
  const me = useQuery({ queryKey: ['me', token], enabled: !!token, queryFn: ({ signal }) => request('/api/auth/me', { signal }), retry: false })
  const logout = () => { session.clear(); setToken(null); client.clear() }
  useEffect(() => {
    const reset = () => { setToken(null); client.clear() }
    const sync = () => { setToken(session.get()); client.clear() }
    window.addEventListener('mrp:logout', reset)
    window.addEventListener('storage', sync)
    return () => { window.removeEventListener('mrp:logout', reset); window.removeEventListener('storage', sync) }
  }, [client])
  const login = async (values) => {
    const result = await request('/api/auth/login', { method: 'POST', body: values })
    client.clear()
    session.set(result.accessToken)
    setToken(result.accessToken)
    return result.user
  }
  if (token && me.isPending) return <div className="center"><Spin tip="Đang xác thực"><div /></Spin></div>
  if (token && me.isError) {
    return (
      <div className="center-screen">
        <Alert type="error" showIcon message="Không tải được thông tin đăng nhập" description={me.error.message}
          action={<Space direction="vertical"><Button size="small" onClick={() => me.refetch()}>Thử lại</Button><Button size="small" onClick={logout}>Đăng xuất</Button></Space>} />
      </div>
    )
  }
  return <AuthContext.Provider value={{ user: token ? me.data : null, login, logout }}>{children}</AuthContext.Provider>
}