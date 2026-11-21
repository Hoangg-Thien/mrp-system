import { useQuery } from '@tanstack/react-query'
import { api } from './client'
import { fetchAllPages } from './allPages'

// The API accepts one status per request and enforces the staff assignment scope.
// Fetch every page of each open status before exposing options to the form.
export function useOpenProductionRequests() {
  return useQuery({
    queryKey: ['staff-open-production-request-options'],
    queryFn: async ({ signal }) => {
      const results = await Promise.all(['PENDING', 'IN_PROGRESS'].map((status) =>
        fetchAllPages((page, size) => api.get('/api/production-requests', {
          status, page, size, sort: 'id', direction: 'desc',
        }, signal)),
      ))
      const byId = new Map(results.flatMap((result) => result.content).map((pr) => [pr.id, pr]))
      return { content: [...byId.values()].sort((a, b) => b.id - a.id) }
    },
  })
}
