import { useQuery } from '@tanstack/react-query'
import { api } from './client'
import { fetchAllPages } from './allPages'

export function useAllPages({ queryKey, path, params = {}, enabled = true }) {
  return useQuery({
    queryKey: [...queryKey, 'all-pages', params],
    queryFn: ({ signal }) => fetchAllPages((page, size) => api.get(path, { ...params, page, size }, signal)),
    enabled,
  })
}
