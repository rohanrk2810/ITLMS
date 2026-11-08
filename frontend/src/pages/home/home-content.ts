import {
  AwardIcon,
  BriefcaseIcon,
  CodeIcon,
  ClipboardCheckIcon,
  type LucideIcon,
  UsersIcon,
  VideoIcon,
} from 'lucide-react'

export interface Feature {
  icon: LucideIcon
  title: string
  text: string
}

/** Nothing here is a figure about results: only what the platform does. Put real numbers in once the institute has them. */
export const FEATURES: Feature[] = [
  { icon: VideoIcon, title: 'Live classes', text: 'Attend from anywhere, ask questions in the room, and watch the recording if you miss one.' },
  { icon: CodeIcon, title: 'Practical, hands-on', text: 'Write and run code in the browser and build real projects instead of only reading slides.' },
  { icon: ClipboardCheckIcon, title: 'Tests and assignments', text: 'Regular quizzes, coding tests and assignments with clear feedback on every submission.' },
  { icon: UsersIcon, title: 'Small batches', text: 'Trainers who know you by name, with attendance and progress tracked for every student.' },
  { icon: AwardIcon, title: 'Verified certificates', text: 'Every certificate has a number and QR code that anyone can check online.' },
  { icon: BriefcaseIcon, title: 'Placement support', text: 'Resume help, interview preparation and job openings shared with our students.' },
]

export const STEPS = [
  { title: 'Enquire', text: 'Tell us what you want to learn. A counsellor calls you back.' },
  { title: 'Get guidance', text: 'Pick the right course and batch for your goal and your time.' },
  { title: 'Learn and practise', text: 'Live classes, notes, tests and projects, with progress you can see.' },
  { title: 'Certificate and career', text: 'Finish, get a verifiable certificate, and move on to placement help.' },
]

export const NAV = [
  { href: '#about', label: 'About' },
  { href: '#courses', label: 'Courses' },
  { href: '#why', label: 'Why us' },
  { href: '#how', label: 'How it works' },
  { href: '#contact', label: 'Contact' },
]

export const DEFAULT_TAGLINE = 'Learn skills that get you hired.'

export const ABOUT_POINTS = [
  'Taught by working trainers, not just textbooks.',
  'Classroom and online batches that fit your schedule.',
  'Every lesson, test and certificate in one place, on your phone too.',
]

