export type LegalSlug = 'privacy' | 'terms' | 'cookies' | 'refund'

export interface LegalSection {
  heading: string
  /** Paragraphs; a string starting with "- " is rendered as a bullet. */
  body: string[]
}

export interface LegalDoc {
  title: string
  summary: string
  sections: LegalSection[]
}

export const LEGAL_UPDATED = '1 November 2026'

/** Plain-language starting texts. The institute must have them reviewed against its own practice and local law. */
export const LEGAL_DOCS: Record<LegalSlug, LegalDoc> = {
  privacy: {
    title: 'Privacy Policy',
    summary: 'What we collect, why, who sees it, and your choices (Digital Personal Data Protection Act, 2023, India).',
    sections: [
      {
        heading: 'What data we collect',
        body: [
          '- Account: name, email, phone, password (stored only as a hash).',
          '- Profile: date of birth, gender, education, college, address, guardian and emergency contacts, and your photo (used on the ID card).',
          '- Learning: courses, lesson progress and watch time, attendance, assignments, test answers and scores, certificates.',
          '- Fees: fee plans, instalments and payment records. We do not store card numbers.',
          '- Test monitoring (only during a monitored test or live class): camera and microphone checks, face detection, tab and window switches, and typing activity. Monitoring settings are chosen by the institute.',
          '- Technical: IP address, browser and device type, and sign-in and audit logs, used for security.',
        ],
      },
      {
        heading: 'Why we use it',
        body: [
          '- To run your admission, classes, tests, certificates and fee records.',
          '- To keep tests fair and the platform secure.',
          '- To contact you about classes, results, fees and announcements.',
          'We do not sell your data and do not use it for advertising.',
        ],
      },
      {
        heading: 'Who can see it',
        body: [
          'Your trainers, coordinators and administrators see the data they need for their role. Certificates and ID cards can be verified publicly using their number, and show only the details printed on them.',
          'Some features use outside services: YouTube or other video hosts for lessons, and a video-meeting service for live classes. Those providers handle the data needed to deliver the video.',
        ],
      },
      {
        heading: 'How long we keep it',
        body: [
          'Academic and fee records are kept while you are a student and for the period the institute needs them for certificates, audits and legal duties. Monitoring recordings and logs are kept for a limited period and then deleted.',
        ],
      },
      {
        heading: 'Your rights',
        body: [
          'You may ask to see, correct or delete your personal data, withdraw consent, or raise a complaint. Contact us using the details below. Some records cannot be deleted while we are legally required to keep them.',
          'For a student under 18, a parent or guardian gives consent and may exercise these rights.',
        ],
      },
      {
        heading: 'Security',
        body: [
          'Access is controlled by role, data is sent over HTTPS, and sign-ins and changes are logged. No system is perfectly secure, so please keep your password private.',
        ],
      },
    ],
  },
  terms: {
    title: 'Terms and Conditions',
    summary: 'The rules for using this learning platform.',
    sections: [
      {
        heading: 'Your account',
        body: [
          'Give correct information and keep your password private. You are responsible for activity under your account. One account belongs to one person; do not share it.',
        ],
      },
      {
        heading: 'Acceptable use',
        body: [
          '- Do not cheat in tests, use unauthorised help, or try to bypass monitoring.',
          '- Do not upload anything unlawful, harmful or that you have no right to share.',
          '- Do not try to access other people\'s data or disrupt the platform.',
          'Breaking these rules can lead to a test being cancelled, an account being suspended, or a certificate being withheld.',
        ],
      },
      {
        heading: 'Course material and ownership',
        body: [
          'Lessons, notes, videos and tests belong to the institute or its trainers. You may use them for your own learning only. Do not record, copy, resell or share them.',
          'You keep ownership of what you submit, and give the institute permission to store and review it for teaching and assessment.',
        ],
      },
      {
        heading: 'Tests and monitoring',
        body: [
          'Some tests and live classes use camera, microphone, screen and tab monitoring. You will be told before it starts. Results flagged by monitoring may be reviewed by a person before any action is taken.',
        ],
      },
      {
        heading: 'Fees and refunds',
        body: ['Fees, instalments and refunds are covered by the Refund Policy.'],
      },
      {
        heading: 'Certificates',
        body: [
          'A certificate is issued after the course requirements are met and fees are cleared. It can be verified online using its number.',
        ],
      },
      {
        heading: 'Limits and changes',
        body: [
          'We try to keep the platform available but cannot promise it will never be interrupted. We may update these terms; continuing to use the platform means you accept the new version. Disputes are subject to the laws of India and the courts of the institute\'s city.',
        ],
      },
    ],
  },
  cookies: {
    title: 'Cookie Policy',
    summary: 'The small files and browser storage this site uses.',
    sections: [
      {
        heading: 'What we use',
        body: [
          '- Essential: keeps you signed in and protects forms (including the sign-in check). The site cannot work without these.',
          '- Preferences: remembers your theme (light or dark) and your cookie choice.',
          'We do not use advertising or cross-site tracking cookies.',
        ],
      },
      {
        heading: 'Third-party content',
        body: [
          'Embedded video (for example YouTube) and the live-class video service may set their own cookies when you play them. We load YouTube in its privacy-enhanced mode where possible.',
        ],
      },
      {
        heading: 'Your choice',
        body: [
          'Essential storage is always on. You can clear it any time in your browser settings, which will sign you out. You can change your cookie choice by clearing this site\'s data and reloading.',
        ],
      },
    ],
  },
  refund: {
    title: 'Refund Policy',
    summary: 'When fees can be refunded.',
    sections: [
      {
        heading: 'Before the course starts',
        body: [
          'If you withdraw before your batch begins, the admission or registration fee may be kept and the remaining course fee refunded.',
        ],
      },
      {
        heading: 'After the course starts',
        body: [
          'Once classes have begun, fees for the period already attended are not refunded. A refund of the unattended portion is decided by the institute case by case.',
        ],
      },
      {
        heading: 'How to ask',
        body: [
          'Write to us using the contact details below with your name, batch and reason. We reply within 7 working days. Approved refunds are paid to the original payment method within 15 working days.',
        ],
      },
      {
        heading: 'Not refundable',
        body: ['Fees for issued certificates, exam or external fees already paid on your behalf, and payment-gateway charges.'],
      },
    ],
  },
}
