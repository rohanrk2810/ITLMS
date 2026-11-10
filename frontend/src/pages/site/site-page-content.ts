/**
 * Text for the public pages that are not driven by the institute's own data: a short blog, the career guide, a list of
 * free learning sites and a default set of questions. It is general guidance only and makes no claim about the
 * institute's results. The FAQ and the success stories come from Customize > Website when the administrator has
 * written them.
 */

export const LEVEL_BLURB: Record<string, string> = {
  BEGINNER: 'New to the subject? Start here. No earlier knowledge is needed.',
  INTERMEDIATE: 'You know the basics and want to build real projects.',
  ADVANCED: 'Go deep, work on harder problems and get ready for interviews.',
}

export const BLOG_POSTS: { title: string; summary: string; body: string[] }[] = [
  {
    title: 'How to choose your first programming language',
    summary: 'The best first language is the one that leads to the work you want to do.',
    body: [
      'Do not look for the perfect language. Look for the work you want to do. Websites lead to HTML, CSS and JavaScript. Business software and Android lead to Java. Data and automation lead to Python.',
      'Whatever you pick, the ideas carry over: variables, conditions, loops, functions and data structures look much the same everywhere. A second language is far easier than the first.',
      'Pick one, stay with it for three months, and build something small every week. That matters more than the choice itself.',
    ],
  },
  {
    title: 'Why you should build projects while you learn',
    summary: 'Watching lessons feels like progress. Building things is progress.',
    body: [
      'It is easy to feel that you understand a topic while you watch someone explain it. The test is whether you can do it on your own, with an empty file.',
      'After every topic, build something with it, however small: a calculator, a to-do list, a page that reads data from a form. When you get stuck, you find the gaps in what you know, and fixing them is where learning happens.',
      'Keep your projects in one place with a short description of each. They become the best part of your resume.',
    ],
  },
  {
    title: 'How to prepare for a technical interview',
    summary: 'A simple plan for the weeks before your first interviews.',
    body: [
      'Start with the basics of the language you know best, and be ready to explain them in plain words. Interviewers care more about clear thinking than about memorised answers.',
      'Practise a few problems every day, then explain your solution out loud. Write down the questions that stumped you and return to them a week later.',
      'Be ready to talk about your projects: what you built, what went wrong and what you would change. Then ask for a mock interview from a friend or a trainer before the real one.',
    ],
  },
]

export const CAREER_STEPS: { title: string; text: string }[] = [
  {
    title: 'Pick a direction',
    text: 'Web, Java and enterprise software, data, testing or mobile. Read what each job involves day to day and choose one to try first. You can change later.',
  },
  {
    title: 'Learn the fundamentals properly',
    text: 'One language, plus the basics of data structures, databases and version control. A strong base makes every later topic easier.',
  },
  {
    title: 'Build projects and keep them online',
    text: 'Two or three projects you can explain well are worth more than ten you cannot. Put the code in a public repository with a short description.',
  },
  {
    title: 'Make a clear resume',
    text: 'One page. Your skills, your projects with links, your education. Describe what you did and what it achieved, in plain sentences.',
  },
  {
    title: 'Practise interviews',
    text: 'Solve problems aloud, explain your projects and ask for feedback. Mock interviews with a trainer or a friend are the quickest way to improve.',
  },
  {
    title: 'Apply, learn from each reply and keep learning',
    text: 'Apply regularly, note what each interview asked, and fill the gaps. The first job is the start of learning on the job, not the end of it.',
  },
]

export const FREE_RESOURCES: { title: string; text: string; href: string }[] = [
  { title: 'MDN Web Docs', text: 'Clear guides and references for HTML, CSS and JavaScript.', href: 'https://developer.mozilla.org/' },
  { title: 'freeCodeCamp', text: 'Free lessons and projects for web development, data and more.', href: 'https://www.freecodecamp.org/' },
  { title: 'roadmap.sh', text: 'Step-by-step roadmaps showing what to learn for each IT role.', href: 'https://roadmap.sh/' },
  { title: 'The Java Tutorials (Oracle)', text: 'The official tutorials for the Java language.', href: 'https://docs.oracle.com/javase/tutorial/' },
  { title: 'The Python Tutorial', text: 'The official introduction to Python.', href: 'https://docs.python.org/3/tutorial/' },
  { title: 'Pro Git (free book)', text: 'Learn version control with Git, from the basics onwards.', href: 'https://git-scm.com/book' },
  { title: 'SQLBolt', text: 'Short interactive lessons for learning SQL.', href: 'https://sqlbolt.com/' },
]

export const DEFAULT_FAQS: { q: string; a: string }[] = [
  {
    q: 'How do I join a course?',
    a: 'Open Courses, choose a course and press Buy. You can pay online, or send an inquiry and the team will call you back.',
  },
  {
    q: 'Can I pay online?',
    a: 'Yes, with UPI, card or net banking. When the payment is confirmed you are added to a batch and the course opens in your portal.',
  },
  {
    q: 'Will I get a receipt?',
    a: 'Yes. A PDF receipt is emailed to you after every payment, and you can download it again from Fees in your portal.',
  },
  {
    q: 'How do I sign in?',
    a: 'Use Sign in at the top of the page with the email you registered with. If you forgot your password, use the Forgot password link on the sign-in page.',
  },
  {
    q: 'How can I check that a certificate is genuine?',
    a: 'Every certificate has a number and a verification code. Open the verification page and enter them, or scan the QR code on the certificate.',
  },
  {
    q: 'Who can I ask if I have more questions?',
    a: 'Send us a message from the Contact page and the team will reply.',
  },
]
