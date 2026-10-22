import { useQuery } from '@tanstack/react-query'

import { fetchPlacement } from '@/api/batches'
import { useAuthStore } from '@/stores/auth-store'

/**
 * True for a student who has not been placed in a batch yet: a new account that has only asked for a course.
 * The portal shows such a student the course list and nothing else until an administrator approves a request.
 * If the answer cannot be read the student is treated as placed, so a failed lookup never locks anyone out.
 */
export function useUnplacedStudent(): boolean {
  const role = useAuthStore((state) => state.user?.role)
  const query = useQuery({
    queryKey: ['me', 'placement'],
    queryFn: fetchPlacement,
    enabled: role === 'STUDENT',
    staleTime: 30_000,
    refetchOnWindowFocus: true,
    retry: 1,
  })
  return role === 'STUDENT' && query.data?.placed === false
}
