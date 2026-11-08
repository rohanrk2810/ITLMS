import {
  AwardIcon,
  BriefcaseIcon,
  ClipboardCheckIcon,
  CodeIcon,
  type LucideIcon,
  UsersIcon,
  VideoIcon,
} from 'lucide-react'

import type { Item, T } from '@/lib/site-config'

/** Nothing here is a figure about results: only what the platform does. The administrator can replace all of it. */
export const DEFAULT_FEATURES: Item[] = [
  { title: 'Live classes', text: 'Attend from anywhere, ask questions in the room, and watch the recording if you miss one.' },
  { title: 'Practical, hands-on', text: 'Write and run code in the browser and build real projects instead of only reading slides.' },
  { title: 'Tests and assignments', text: 'Regular quizzes, coding tests and assignments with clear feedback on every submission.' },
  { title: 'Small batches', text: 'Trainers who know you by name, with attendance and progress tracked for every student.' },
  { title: 'Verified certificates', text: 'Every certificate has a number and QR code that anyone can check online.' },
  { title: 'Placement support', text: 'Resume help, interview preparation and job openings shared with our students.' },
]
export const FEATURE_ICONS: LucideIcon[] = [VideoIcon, CodeIcon, ClipboardCheckIcon, UsersIcon, AwardIcon, BriefcaseIcon]

export const DEFAULT_STEPS: Item[] = [
  { title: 'Enquire', text: 'Tell us what you want to learn. A counsellor calls you back.' },
  { title: 'Get guidance', text: 'Pick the right course and batch for your goal and your time.' },
  { title: 'Learn and practise', text: 'Live classes, notes, tests and projects, with progress you can see.' },
  { title: 'Certificate and career', text: 'Finish, get a verifiable certificate, and move on to placement help.' },
]

export const DEFAULT_ABOUT_POINTS: T[] = [
  'Taught by working trainers, not just textbooks.',
  'Classroom and online batches that fit your schedule.',
  'Every lesson, test and certificate in one place, on your phone too.',
]

export const DEFAULT_HERO_BADGE = 'Admissions open'
export const DEFAULT_HERO_TITLE = 'Learn skills that get you hired.'
export const DEFAULT_HERO_TEXT = 'Live classes, hands-on projects, regular tests and a certificate you can prove, all in one place.'
export const DEFAULT_ABOUT_INTRO = 'A place to learn IT skills properly and get ready for work.'
export const DEFAULT_WHATSAPP_MESSAGE = 'Hello, I would like to know about your courses.'

/** Section ids that have a link in the top bar. */
export const NAV_IDS = ['about', 'courses', 'why', 'how', 'gallery', 'faculty', 'testimonials', 'faq', 'contact'] as const

/** Every fixed word on the page. */
export const S = {
  nav: { about: 'About', courses: 'Courses', why: 'Why us', how: 'How it works', gallery: 'Gallery', faculty: 'Trainers', testimonials: 'Feedback', faq: 'FAQ', contact: 'Contact' },
  login: 'Login',
  register: 'Register',
  skip: 'Skip to content',
  openMenu: 'Open menu',
  closeMenu: 'Close menu',
  counselling: 'Get free counselling',
  explore: 'Explore courses',
  statCourses: 'Courses',
  statClasses: 'Classes',
  statCertificates: 'Verified certificates',
  statNotes: 'Notes and recordings',
  aboutTitle: 'About',
  coursesTitle: 'Our courses',
  coursesIntro: 'Pick a course, see what you will learn, and ask us anything.',
  coursesFailed: 'Courses could not be loaded right now. Please try again in a moment.',
  coursesNone: 'New courses are being added. Ask us what is starting next.',
  askAbout: 'Ask about this',
  hours: 'hours',
  fee: 'Fee',
  syllabus: 'Ask us for the full syllabus.',
  whyTitle: 'Why learn with us',
  howTitle: 'How it works',
  galleryTitle: 'Life at our institute',
  facultyTitle: 'Meet our trainers',
  partnersTitle: 'Where our students work',
  testimonialsTitle: 'What our students say',
  faqTitle: 'Questions and answers',
  enquiryTitle: 'Talk to a counsellor',
  enquiryIntro: 'Leave your number and we will call you back. It is free.',
  contactTitle: 'Contact us',
  visit: 'Visit us',
  call: 'Call us',
  email: 'Email us',
  student: 'Already a student?',
  studentText: 'Login to your classes, tests and certificates',
  formName: 'Your name',
  formPhone: 'Phone number',
  formEmail: 'Email (optional)',
  formCourse: 'Course you are interested in',
  formNotSure: 'Not sure yet',
  formMessage: 'Message (optional)',
  formSend: 'Request a call back',
  formSending: 'Sending...',
  formAgree: 'By sending this you agree to our',
  privacy: 'Privacy Policy',
  thanks: 'Thank you!',
  thanksText: 'We have your details and will call you back soon.',
  formFailed: 'Could not send your enquiry. Please try again.',
  whatsapp: 'Chat on WhatsApp',
  follow: 'Follow us',
  rights: 'All rights reserved.',
  close: 'Close',
  openMap: 'Open in Google Maps',
} as const

export type Strings = typeof S
