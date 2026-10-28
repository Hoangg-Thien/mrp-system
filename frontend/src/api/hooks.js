import { useSingleMutation as useMutation } from './useSingleMutation'
import { useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { App } from 'antd'
import { api } from './client'

/** Query danh sách có phân trang; giữ dữ liệu cũ khi đổi trang/lọc để không nháy bảng. */
export function usePagedQuery(key, path, params) {
  return useQuery({
    queryKey: [key, params],
    queryFn: ({ signal }) => api.get(path, params, signal),
    placeholderData: keepPreviousData,
  })
}

/**
 * Nhóm mutation CRUD + action (activate/deactivate/lock...) cho một resource.
 * Lỗi hiển thị bằng message từ ProblemDetail của backend; frontend không tự suy luận nghiệp vụ.
 */
export function useResourceActions({ key, path, invalidateKeys }) {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const keys = invalidateKeys || [key]
  const refresh = () => keys.forEach((item) => queryClient.invalidateQueries({ queryKey: [item] }))

  const onError = (error) => message.error(error.message)
  const create = useMutation({
    mutationFn: (body) => api.post(path, body),
    onSuccess: () => { message.success('Thêm mới thành công'); refresh() },
    onError,
  })
  const update = useMutation({
    mutationFn: ({ id, body }) => api.put(`${path}/${id}`, body),
    onSuccess: () => { message.success('Cập nhật thành công'); refresh() },
    onError,
  })
  const remove = useMutation({
    mutationFn: (id) => api.del(`${path}/${id}`),
    onSuccess: () => { message.success('Đã xóa thành công'); refresh() },
    onError,
  })
  const runAction = useMutation({
    mutationFn: ({ id, name, body }) => api.post(`${path}/${id}/${name}`, body),
    onSuccess: () => { message.success('Cập nhật trạng thái thành công'); refresh() },
    onError,
  })

  return { message, create, update, remove, runAction, refresh }
}
