import {
  Award,
  Banknote,
  BookOpen,
  Briefcase,
  CalendarClock,
  ClipboardList,
  FileText,
  Inbox,
  LayoutDashboard,
  Megaphone,
  ShieldCheck,
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
  {
    label: 'Course requests',
    to: '/app/course-requests',
    icon: Inbox,
    // A student's own requests, or the queue that administrators and coordinators decide.
    roles: ['ADMIN', 'COORDINATOR', 'STUDENT'],
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
  {
    label: 'Finance',
    to: '/app/finance',
    icon: Banknote,
    // A student's own fees, or the finance desk for everyone who runs it (Doc S6.12).
    roles: ['ADMIN', 'COORDINATOR', 'FINANCE', 'STUDENT'],
  },
  { label: 'Certificates', to: '/app/certificates', icon: Award, roles: ['ADMIN', 'COORDINATOR', 'STUDENT'] },
  { label: 'Placements', to: '/app/placements', icon: Briefcase, roles: ['ADMIN', 'PLACEMENT', 'STUDENT'] },
  { label: 'Files', to: '/app/files', icon: FileText, roles: ALL_ROLES },
  { label: 'Announcements', to: '/app/announcements', icon: Megaphone, roles: ALL_ROLES },
  {
    label: 'Users',
    to: '/app/users',
    icon: ShieldCheck,
    // List/get is Roles.STAFF; create, status and password-reset are ADMIN only (enforced in the page itself).
    roles: ['ADMIN', 'COORDINATOR'],
  },
]
