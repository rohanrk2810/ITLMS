import {
  AwardIcon,
  BriefcaseIcon,
  ClipboardCheckIcon,
  CodeIcon,
  type LucideIcon,
  UsersIcon,
  VideoIcon,
} from 'lucide-react'

import type { Item, Lang, T } from '@/lib/site-config'

const t = (en: string, mr: string): T => ({ en, mr })

/** Nothing here is a figure about results: only what the platform does. The administrator can replace all of it. */
export const DEFAULT_FEATURES: Item[] = [
  { title: t('Live classes', 'लाईव्ह क्लासेस'), text: t('Attend from anywhere, ask questions in the room, and watch the recording if you miss one.', 'कुठूनही हजर रहा, वर्गात प्रश्न विचारा, आणि चुकला तर रेकॉर्डिंग पाहा.') },
  { title: t('Practical, hands-on', 'प्रत्यक्ष सराव'), text: t('Write and run code in the browser and build real projects instead of only reading slides.', 'ब्राउझरमध्येच कोड लिहा आणि चालवा, फक्त स्लाईड्स न वाचता खरे प्रोजेक्ट्स बनवा.') },
  { title: t('Tests and assignments', 'टेस्ट आणि असाइनमेंट'), text: t('Regular quizzes, coding tests and assignments with clear feedback on every submission.', 'नियमित क्विझ, कोडिंग टेस्ट आणि असाइनमेंट, प्रत्येक सबमिशनवर स्पष्ट अभिप्राय.') },
  { title: t('Small batches', 'लहान बॅचेस'), text: t('Trainers who know you by name, with attendance and progress tracked for every student.', 'तुम्हाला नावाने ओळखणारे ट्रेनर, प्रत्येक विद्यार्थ्याची उपस्थिती आणि प्रगती नोंदवलेली.') },
  { title: t('Verified certificates', 'पडताळणी करता येणारी प्रमाणपत्रे'), text: t('Every certificate has a number and QR code that anyone can check online.', 'प्रत्येक प्रमाणपत्रावर नंबर आणि QR कोड असतो, जो कोणीही ऑनलाईन तपासू शकतो.') },
  { title: t('Placement support', 'प्लेसमेंट मदत'), text: t('Resume help, interview preparation and job openings shared with our students.', 'रेझ्युमे मदत, मुलाखतीची तयारी आणि नोकरीच्या संधी विद्यार्थ्यांना कळवल्या जातात.') },
]
export const FEATURE_ICONS: LucideIcon[] = [VideoIcon, CodeIcon, ClipboardCheckIcon, UsersIcon, AwardIcon, BriefcaseIcon]

export const DEFAULT_STEPS: Item[] = [
  { title: t('Enquire', 'चौकशी करा'), text: t('Tell us what you want to learn. A counsellor calls you back.', 'तुम्हाला काय शिकायचं ते सांगा. काउन्सेलर तुम्हाला फोन करतील.') },
  { title: t('Get guidance', 'मार्गदर्शन घ्या'), text: t('Pick the right course and batch for your goal and your time.', 'तुमचं ध्येय आणि वेळ पाहून योग्य कोर्स आणि बॅच निवडा.') },
  { title: t('Learn and practise', 'शिका आणि सराव करा'), text: t('Live classes, notes, tests and projects, with progress you can see.', 'लाईव्ह क्लासेस, नोट्स, टेस्ट आणि प्रोजेक्ट्स, आणि तुमची प्रगती दिसते.') },
  { title: t('Certificate and career', 'प्रमाणपत्र आणि करिअर'), text: t('Finish, get a verifiable certificate, and move on to placement help.', 'कोर्स पूर्ण करा, पडताळता येणारं प्रमाणपत्र मिळवा आणि प्लेसमेंट मदतीकडे वळा.') },
]

export const DEFAULT_ABOUT_POINTS: T[] = [
  t('Taught by working trainers, not just textbooks.', 'पुस्तकी ज्ञानासोबत प्रत्यक्ष काम करणाऱ्या ट्रेनरकडून शिकवणी.'),
  t('Classroom and online batches that fit your schedule.', 'तुमच्या वेळेला जुळणाऱ्या क्लासरूम आणि ऑनलाईन बॅचेस.'),
  t('Every lesson, test and certificate in one place, on your phone too.', 'प्रत्येक धडा, टेस्ट आणि प्रमाणपत्र एकाच ठिकाणी, मोबाईलवरही.'),
]

export const DEFAULT_HERO_BADGE = t('Admissions open', 'प्रवेश सुरू आहेत')
export const DEFAULT_HERO_TITLE = t('Learn skills that get you hired.', 'नोकरी मिळवून देणारी कौशल्ये शिका.')
export const DEFAULT_HERO_TEXT = t(
  'Live classes, hands-on projects, regular tests and a certificate you can prove, all in one place.',
  'लाईव्ह क्लासेस, प्रत्यक्ष प्रोजेक्ट्स, नियमित टेस्ट आणि सिद्ध करता येणारं प्रमाणपत्र, सगळं एकाच ठिकाणी.',
)
export const DEFAULT_ABOUT_INTRO = t(
  'A place to learn IT skills properly and get ready for work.',
  'आयटी कौशल्ये नीट शिकण्याची आणि कामासाठी तयार होण्याची जागा.',
)
export const DEFAULT_WHATSAPP_MESSAGE = t('Hello, I would like to know about your courses.', 'नमस्कार, मला तुमच्या कोर्सेसबद्दल माहिती हवी आहे.')

/** Section ids that have a link in the top bar. */
export const NAV_IDS = ['about', 'courses', 'why', 'how', 'gallery', 'faculty', 'testimonials', 'faq', 'contact'] as const

/** Every fixed word on the page, in both languages. */
const STRINGS = {
  en: {
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
    switchTo: 'मराठी',
    close: 'Close',
    openMap: 'Open in Google Maps',
  },
  mr: {
    nav: { about: 'आमच्याबद्दल', courses: 'कोर्सेस', why: 'का आम्ही', how: 'कसं चालतं', gallery: 'फोटो', faculty: 'ट्रेनर', testimonials: 'अभिप्राय', faq: 'प्रश्नोत्तरे', contact: 'संपर्क' },
    login: 'लॉगिन',
    register: 'नोंदणी',
    skip: 'मजकुराकडे जा',
    openMenu: 'मेनू उघडा',
    closeMenu: 'मेनू बंद करा',
    counselling: 'मोफत मार्गदर्शन घ्या',
    explore: 'कोर्सेस पाहा',
    statCourses: 'कोर्सेस',
    statClasses: 'क्लासेस',
    statCertificates: 'पडताळणी करता येणारी प्रमाणपत्रे',
    statNotes: 'नोट्स आणि रेकॉर्डिंग',
    aboutTitle: 'आमच्याबद्दल',
    coursesTitle: 'आमचे कोर्सेस',
    coursesIntro: 'कोर्स निवडा, काय शिकाल ते पाहा आणि आम्हाला काहीही विचारा.',
    coursesFailed: 'कोर्सेस आत्ता लोड होऊ शकले नाहीत. कृपया थोड्या वेळाने पुन्हा प्रयत्न करा.',
    coursesNone: 'नवीन कोर्सेस जोडले जात आहेत. पुढे काय सुरू होतंय ते आम्हाला विचारा.',
    askAbout: 'याबद्दल विचारा',
    hours: 'तास',
    fee: 'फी',
    syllabus: 'संपूर्ण अभ्यासक्रमासाठी आम्हाला विचारा.',
    whyTitle: 'आमच्याकडे का शिकावं',
    howTitle: 'हे कसं चालतं',
    galleryTitle: 'आमच्या संस्थेतील क्षण',
    facultyTitle: 'आमचे ट्रेनर',
    partnersTitle: 'आमचे विद्यार्थी कुठे काम करतात',
    testimonialsTitle: 'आमचे विद्यार्थी काय म्हणतात',
    faqTitle: 'प्रश्न आणि उत्तरे',
    enquiryTitle: 'काउन्सेलरशी बोला',
    enquiryIntro: 'तुमचा नंबर द्या, आम्ही तुम्हाला फोन करू. हे मोफत आहे.',
    contactTitle: 'संपर्क करा',
    visit: 'भेट द्या',
    call: 'फोन करा',
    email: 'ईमेल करा',
    student: 'आधीच विद्यार्थी आहात?',
    studentText: 'तुमचे क्लासेस, टेस्ट आणि प्रमाणपत्रांसाठी लॉगिन करा',
    formName: 'तुमचं नाव',
    formPhone: 'फोन नंबर',
    formEmail: 'ईमेल (ऐच्छिक)',
    formCourse: 'तुम्हाला कोणत्या कोर्समध्ये रस आहे',
    formNotSure: 'अजून ठरवलं नाही',
    formMessage: 'संदेश (ऐच्छिक)',
    formSend: 'मला फोन करा',
    formSending: 'पाठवत आहे...',
    formAgree: 'हे पाठवून तुम्ही आमचं',
    privacy: 'गोपनीयता धोरण',
    thanks: 'धन्यवाद!',
    thanksText: 'आम्हाला तुमची माहिती मिळाली आहे, आम्ही लवकरच फोन करू.',
    formFailed: 'तुमची चौकशी पाठवता आली नाही. कृपया पुन्हा प्रयत्न करा.',
    whatsapp: 'WhatsApp वर बोला',
    follow: 'आम्हाला फॉलो करा',
    rights: 'सर्व हक्क राखीव.',
    switchTo: 'English',
    close: 'बंद करा',
    openMap: 'Google Maps मध्ये उघडा',
  },
} as const

export type Strings = (typeof STRINGS)['en']
export const strings = (lang: Lang): Strings => STRINGS[lang] as Strings
