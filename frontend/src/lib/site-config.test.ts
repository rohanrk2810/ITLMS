import { describe, expect, it } from 'vitest'

import { parseSite, pick, safeUrl } from './site-config'

describe('parseSite', () => {
  it.each([[null], [undefined], [''], ['nope'], ['[]'], ['7']])('falls back to the defaults for %j', (text) => {
    const site = parseSite(text)
    expect(site.stats).toBeUndefined()
    expect(site.features).toBeUndefined()
    expect(site.gallery).toEqual([])
    expect(site.languageSwitch).toBe(true)
    expect(site.courseCards.showDuration).toBe(true)
    expect(site.courseCards.showFee).toBe(false)
  })

  it('keeps both languages, drops blanks and caps the lists', () => {
    const many = Array.from({ length: 30 }, (_, i) => ({ image: String(i + 1), caption: { en: 'x', mr: 'य' } }))
    const site = parseSite(
      JSON.stringify({
        heroTitle: { en: 'Learn. Build.', mr: 'शिका. घडा.' },
        gallery: [...many, { image: '', caption: { en: 'no picture', mr: '' } }],
        faqs: [{ q: { en: 'Fees?', mr: '' }, a: { en: 'Ask us', mr: '' } }, { q: { en: '', mr: '' }, a: { en: 'orphan', mr: '' } }],
        stats: [{ value: '', label: { en: '', mr: '' } }],
        hidden: ['faq'],
      }),
    )
    expect(site.heroTitle).toEqual({ en: 'Learn. Build.', mr: 'शिका. घडा.' })
    expect(site.gallery).toHaveLength(18)
    expect(site.faqs).toHaveLength(1)
    expect(site.stats).toBeUndefined()
    expect(site.hidden).toEqual(['faq'])
  })

  it('accepts only picture ids made of digits', () => {
    const site = parseSite(
      JSON.stringify({ heroImage: '../etc/passwd', faculty: [{ name: 'Asha', image: '12' }, { name: 'Ravi', image: 'x1' }] }),
    )
    expect(site.heroImage).toBe('')
    expect(site.faculty.map((f) => f.image)).toEqual(['12', ''])
  })

  it('refuses links that could run script and map addresses that are not Google Maps', () => {
    const site = parseSite(
      JSON.stringify({
        social: { instagram: 'javascript:alert(1)', youtube: 'https://youtube.com/@x' },
        announcement: { enabled: true, text: { en: 'Hi', mr: '' }, link: 'javascript:alert(1)' },
        mapUrl: 'https://evil.example/maps/embed',
      }),
    )
    expect(site.social.instagram).toBe('')
    expect(site.social.youtube).toBe('https://youtube.com/@x')
    expect(site.announcement.link).toBe('')
    expect(site.mapUrl).toBe('')
    expect(parseSite(JSON.stringify({ mapUrl: 'https://www.google.com/maps/embed?pb=abc' })).mapUrl).toContain('google.com/maps/embed')
    expect(safeUrl('/register')).toBe('/register')
  })
})

describe('pick', () => {
  it('uses the chosen language, then the standard wording, then the other language', () => {
    expect(pick({ en: 'Hello', mr: 'नमस्कार' }, 'mr')).toBe('नमस्कार')
    expect(pick({ en: 'Hello', mr: '' }, 'mr', 'standard')).toBe('standard')
    expect(pick({ en: 'Hello', mr: '' }, 'mr')).toBe('Hello')
    expect(pick(undefined, 'en', 'fallback')).toBe('fallback')
  })
})
