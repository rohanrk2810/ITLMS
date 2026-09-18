/**
 * Shapes that mirror the backend DTOs exactly (see
 * services/common-lib/.../ErrorResponse.java and PageResponse.java, and
 * identity-service's AuthResponse/UserResponse). Kept in one file so a
 * backend field rename is one search away from being caught here too.
 */

/** The one error shape every IT-ILMS service returns. */
export interface ApiErrorResponse {
  timestamp: string
  status: number
  error: string
  code: string
  message: string
  path: string
  fieldErrors?: Record<string, string>
  details?: string[]
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

/** The six roles defined in Doc Section 4, exactly as the JWT and every API carry them. */
export type Role = 'ADMIN' | 'COORDINATOR' | 'TRAINER' | 'STUDENT' | 'PLACEMENT' | 'FINANCE'

export interface UserResponse {
  id: number
  firstName: string
  lastName: string
  fullName: string
  email: string
  phone: string
  role: Role
  roleDisplayName: string
  status: string
  profileId: number | null
  profileCode: string | null
  mustChangePassword: boolean
  lastLoginAt: string | null
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: 'Bearer'
  /** Access token lifetime in seconds. */
  expiresIn: number
  mustChangePassword: boolean
  user: UserResponse
}

export interface ApiMessage {
  message: string
}
