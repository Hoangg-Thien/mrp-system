import { useMemo } from 'react'
import { useMutation } from '@tanstack/react-query'
import { singleFlight } from './singleFlight'

export function useSingleMutation(options) {
  const mutation = useMutation(options)
  const mutateAsync = useMemo(() => singleFlight(mutation.mutateAsync), [mutation.mutateAsync])
  return {
    ...mutation,
    mutateAsync,
    mutate: (...args) => { mutateAsync(...args).catch(() => {}) },
  }
}
