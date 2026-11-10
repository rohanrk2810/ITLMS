import { describe, expect, it } from 'vitest'

import { EXAMPLE_JSON, buildPrompt, checkSetup, extractJson, mergeSite } from './institute-setup'
import { DEFAULT_SITE, type SiteConfig } from './site-config'

describe('the example file and the prompt', () => {
  it('the example file passes its own checks, and covers the institute and every part of the website', () => {
    const result = checkSetup(EXAMPLE_JSON)

    expect(result.setup).not.toBeNull()
    expect(result.problems.filter((p) => p.level === 'error')).toEqual([])
    expect(result.setup?.institute.name).toBe('Sprint IT Academy')
    expect(result.setup?.site.faqs.length).toBeGreaterThan(0)
    expect(result.setup?.site.faculty[0].name).toBe('Ravi Kulkarni')
    expect(result.setup?.provided).toContain('faculty')
    expect(result.setup?.provided).toContain('courseCards.showFee')
  })

  it('the prompt names every field, carries the example and leaves room for the institute details', () => {
    const prompt = buildPrompt()

    for (const name of ['institute.name', 'institute.address', 'website.faqs', 'website.trainers', 'website.social', 'website.mapUrl']) {
      expect(prompt).toContain(name)
    }
    expect(prompt).toContain('Sprint IT Academy')
    expect(prompt).toMatch(/MY INSTITUTE/)
    expect(prompt).toMatch(/Never invent facts/)
  })
})

describe('reading a reply', () => {
  it('finds the JSON inside a code block or after a sentence of chat', () => {
    expect(JSON.parse(extractJson('Sure!\n```json\n{"institute":{"name":"A"}}\n```\nAnything else?'))).toEqual({ institute: { name: 'A' } })
    expect(JSON.parse(extractJson('Here it is: {"website":{"heroTitle":"B"}} done'))).toEqual({ website: { heroTitle: 'B' } })
  })

  it('refuses text that is not JSON, and a file with nothing in it', () => {
    expect(checkSetup('not json at all').setup).toBeNull()
    expect(checkSetup('[1,2]').setup).toBeNull()
    expect(checkSetup('{"institute":{},"website":{}}').problems.some((p) => /Nothing to change/.test(p.text))).toBe(true)
  })

  it('checks the institute: a bad colour or email is an error, a long text is shortened, unknown names are ignored', () => {
    const bad = checkSetup('{"institute":{"name":"X","primaryColor":"purple","contactEmail":"nope"}}')
    expect(bad.setup).toBeNull()
    expect(bad.problems.filter((p) => p.level === 'error')).toHaveLength(2)

    const long = checkSetup(JSON.stringify({ institute: { name: 'X'.repeat(200), colour: 'red' } }))
    expect(long.setup?.institute.name).toHaveLength(120)
    expect(long.problems.map((p) => p.text).join(' ')).toMatch(/shortened/)
    expect(long.problems.map((p) => p.text).join(' ')).toMatch(/institute\.colour is not used/)
  })

  it('keeps only what the website can show, and says how many items of a list were kept', () => {
    const faqs = Array.from({ length: 20 }, (_, i) => ({ q: `Question ${i}`, a: `Answer ${i}` }))
    faqs.push({ q: '', a: 'no question' })
    const result = checkSetup(JSON.stringify({ website: { faqs, hiddenSections: ['gallery', 'nonsense'], social: { instagram: 'javascript:alert(1)' } } }))

    expect(result.setup?.site.faqs).toHaveLength(14)
    expect(result.problems.map((p) => p.text).join(' ')).toMatch(/14 of 21 items kept/)
    expect(result.setup?.site.hidden).toEqual(['gallery'])
    expect(result.problems.map((p) => p.text).join(' ')).toMatch(/nonsense/)
    // an address that is not a web address never becomes a link
    expect(result.setup?.site.social.instagram).toBe('')
  })

  it('a summary says what will change', () => {
    const result = checkSetup('{"institute":{"name":"A","address":"B"},"website":{"faqs":[{"q":"Q","a":"A"}]}}')
    expect(result.summary[0]).toMatch(/Institute: Name, Full address/)
    expect(result.summary[1]).toMatch(/Website: Questions and answers \(1\)/)
  })
})

describe('applying a reply to what is already there', () => {
  const current: SiteConfig = {
    ...DEFAULT_SITE,
    heroTitle: 'Old heading',
    aboutIntro: 'Old about',
    faculty: [{ name: 'Ravi Kulkarni', role: 'Old role', image: '41' }, { name: 'Left', role: 'x', image: '42' }],
    gallery: [{ image: '7', caption: 'Lab' }],
    social: { whatsapp: '911', instagram: 'https://old.example/i', youtube: '', facebook: '', linkedin: '' },
    courseCards: { showFee: false, showDuration: true, images: { '1': '9' } },
  }

  it('replaces only the parts that were in the file and leaves the rest', () => {
    const setup = checkSetup('{"website":{"heroTitle":"New heading"}}').setup!
    const merged = mergeSite(current, setup)

    expect(merged.heroTitle).toBe('New heading')
    expect(merged.aboutIntro).toBe('Old about')
    expect(merged.gallery).toEqual(current.gallery)
    expect(merged.courseCards.images).toEqual({ '1': '9' })
  })

  it('a trainer whose name stays keeps the photo, a new one has none, a missing one is gone', () => {
    const setup = checkSetup('{"website":{"trainers":[{"name":"ravi kulkarni","role":"Java"},{"name":"New Person","role":"Python"}]}}').setup!
    const merged = mergeSite(current, setup)

    expect(merged.faculty).toEqual([
      { name: 'ravi kulkarni', role: 'Java', image: '41' },
      { name: 'New Person', role: 'Python', image: '' },
    ])
  })

  it('changes only the social links and switches that were given', () => {
    const setup = checkSetup('{"website":{"social":{"youtube":"https://youtube.com/c/x"},"showCourseFee":true}}').setup!
    const merged = mergeSite(current, setup)

    expect(merged.social.youtube).toBe('https://youtube.com/c/x')
    expect(merged.social.instagram).toBe('https://old.example/i')
    expect(merged.social.whatsapp).toBe('911')
    expect(merged.courseCards.showFee).toBe(true)
    expect(merged.courseCards.showDuration).toBe(true)
  })
})
