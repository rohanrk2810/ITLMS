import {
  Award,
  Banknote,
  BookOpen,
  Briefcase,
  CalendarClock,
  ClipboardList,
  FileText,
  IdCard,
  LayoutDashboard,
  SlidersHorizontal,
  ScanFace,
  Megaphone,
  ShieldCheck,
  TrendingUp,
  UserRound,
  Wallet,
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
  { label: 'My profile', to: '/app/my-profile', icon: UserRound, roles: ['STUDENT'] },
  { label: 'My progress', to: '/app/my-progress', icon: TrendingUp, roles: ['STUDENT'] },
  { label: 'Batches', to: '/app/batches', icon: CalendarClock, roles: ['ADMIN', 'COORDINATOR', 'TRAINER'] },
  {
    label: 'Live classes',
    to: '/app/live-classes',
    icon: Video,
    roles: ['ADMIN', 'COORDINATOR', 'TRAINER', 'STUDENT'],
  },
  {
    label: 'Question bank',
    to: '/app/lecture-material',
    icon: FileText,
    roles: ['ADMIN', 'COORDINATOR', 'TRAINER'],
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
  { label: 'My salary', to: '/app/my-salary', icon: Wallet, roles: ['ADMIN', 'COORDINATOR', 'TRAINER', 'PLACEMENT', 'FINANCE'] },
  { label: 'Certificates', to: '/app/certificates', icon: Award, roles: ['ADMIN', 'STUDENT'] },
  { label: 'Placements', to: '/app/placements', icon: Briefcase, roles: ['ADMIN', 'PLACEMENT', 'STUDENT'] },
  { label: 'Files', to: '/app/files', icon: FileText, roles: ALL_ROLES },
  { label: 'Announcements', to: '/app/announcements', icon: Megaphone, roles: ALL_ROLES },
  { label: 'ID cards', to: '/app/id-cards', icon: IdCard, roles: ['ADMIN', 'COORDINATOR', 'TRAINER', 'STUDENT'] },
  {
    label: 'Users',
    to: '/app/users',
    icon: ShieldCheck,
    // List/get is Roles.STAFF; create, status and password-reset are ADMIN only (enforced in the page itself).
    roles: ['ADMIN', 'COORDINATOR'],
  },
  { label: 'Customize', to: '/app/customize', icon: SlidersHorizontal, roles: ['ADMIN'] },
  { label: 'Monitoring', to: '/app/monitoring', icon: ScanFace, roles: ['ADMIN'] },
]
