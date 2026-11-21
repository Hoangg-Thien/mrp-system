import { useMemo } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { singleFlight } from './singleFlight'

export function useSingleMutation(options) {
  const queryClient = useQueryClient()
  const mutation = useMutation({
    ...options,
    onSuccess: (...args) => {
      try {
        options.onSuccess?.(...args)
      } finally {
        // Refresh active views that depend on this write, including other parts of the workflow.
        void queryClient.invalidateQueries()
      }
    },
  })
  const mutateAsync = useMemo(() => singleFlight(mutation.mutateAsync), [mutation.mutateAsync])
  return {
    ...mutation,
    mutateAsync,
    mutate: (...args) => { mutateAsync(...args).catch(() => {}) },
  }
}
