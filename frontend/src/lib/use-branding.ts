import { useQuery } from '@tanstack/react-query'

import { type Branding, getBranding } from '@/api/branding'

/** What to show until the institute's own settings arrive (or if they never do). */
export const DEFAULT_NAME = 'Learning Management System'

export const BRANDING_KEY = ['branding'] as const

export function useBranding(): { branding: Branding | undefined; name: string } {
  const { data } = useQuery({ queryKey: BRANDING_KEY, queryFn: getBranding, staleTime: 5 * 60_000 })
  return { branding: data, name: data?.name ?? DEFAULT_NAME }
}
