import {
  Award,
  Banknote,
  BookOpen,
  Briefcase,
  CalendarClock,
  ClipboardList,
  FileText,
  LayoutDashboard,
  Megaphone,
  UserPlus,
  Users,
  Video,
} from 'lucide-react'

import type { Role } from '@/api/types'

export interface NavItem {
  label: string
  to: string
  icon: typeof LayoutDashboard
  /** Every role in this list may open it. Enforced again, and for real, on every API call behind it (Doc S12). */
  roles: readonly Role[]
}

const ALL_ROLES: readonly Role[] = ['ADMIN', 'COORDINATOR', 'TRAINER', 'STUDENT', 'PLACEMENT', 'FINANCE']

export const NAV_ITEMS: readonly NavItem[] = [
  { label: 'Dashboard', to: '/app', icon: LayoutDashboard, roles: ALL_ROLES },
  { label: 'Admissions', to: '/app/admissions', icon: UserPlus, roles: ['ADMIN', 'COORDINATOR'] },
  { label: 'Students', to: '/app/students', icon: Users, roles: ['ADMIN', 'COORDINATOR', 'TRAINER'] },
  {
    label: 'Courses',
    to: '/app/courses',
    icon: BookOpen,
    roles: ['ADMIN', 'COORDINATOR', 'TRAINER', 'STUDENT'],
  },
  { label: 'Batches', to: '/app/batches', icon: CalendarClock, roles: ['ADMIN', 'COORDINATOR', 'TRAINER'] },
  {
    label: 'Live classes',
    to: '/app/live-classes',
    icon: Video,
    roles: ['ADMIN', 'COORDINATOR', 'TRAINER', 'STUDENT'],
  },
  {
    label: 'Assessments',
    to: '/app/assessments',
    icon: ClipboardList,
    roles: ['ADMIN', 'COORDINATOR', 'TRAINER', 'STUDENT'],
  },
  { label: 'Finance', to: '/app/finance', icon: Banknote, roles: ['ADMIN', 'COORDINATOR', 'FINANCE'] },
  { label: 'Certificates', to: '/app/certificates', icon: Award, roles: ['ADMIN', 'COORDINATOR', 'STUDENT'] },
  { label: 'Placements', to: '/app/placements', icon: Briefcase, roles: ['ADMIN', 'PLACEMENT', 'STUDENT'] },
  { label: 'Files', to: '/app/files', icon: FileText, roles: ALL_ROLES },
  { label: 'Announcements', to: '/app/announcements', icon: Megaphone, roles: ALL_ROLES },
]
