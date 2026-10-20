import { createContext, useContext } from 'react'

/**
 * Context đăng nhập để ở file riêng: `Auth.jsx` chỉ export component (đúng rule
 * only-export-components / fast refresh). Giá trị: { user, login, logout }.
 */
export const AuthContext = createContext(null)
export const useAuth = () => useContext(AuthContext)
