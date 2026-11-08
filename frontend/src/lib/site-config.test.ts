import { describe, expect, it } from 'vitest'

import { cleanMapUrl, isMapEmbed, parseSite, pick, safeUrl } from './site-config'

describe('parseSite', () => {
  it.each([[null], [undefined], [''], ['nope'], ['[]'], ['7']])('falls back to the defaults for %j', (text) => {
    const site = parseSite(text)
    expect(site.stats).toBeUndefined()
    expect(site.features).toBeUndefined()
    expect(site.gallery).toEqual([])
    expect(site.courseCards.showDuration).toBe(true)
    expect(site.courseCards.showFee).toBe(false)
  })

  it('keeps plain text, drops blanks and caps the lists', () => {
    const many = Array.from({ length: 30 }, (_, i) => ({ image: String(i + 1), caption: 'x' }))
    const site = parseSite(
      JSON.stringify({
        heroTitle: 'Learn. Build.',
        gallery: [...many, { image: '', caption: 'no picture' }],
        faqs: [{ q: 'Fees?', a: 'Ask us' }, { q: '', a: 'orphan' }],
        stats: [{ value: '', label: '' }],
        hidden: ['faq'],
      }),
    )
    expect(site.heroTitle).toBe('Learn. Build.')
    expect(site.gallery).toHaveLength(18)
    expect(site.faqs).toEqual([{ q: 'Fees?', a: 'Ask us' }])
    expect(site.stats).toBeUndefined()
    expect(site.hidden).toEqual(['faq'])
  })

  it('still reads content saved while the site had two languages, keeping the English', () => {
    const site = parseSite(JSON.stringify({ heroTitle: { en: 'Learn. Build.', mr: 'शिका.' }, faqs: [{ q: { en: 'Fees?', mr: '' }, a: { en: 'Ask us', mr: '' } }] }))
    expect(site.heroTitle).toBe('Learn. Build.')
    expect(site.faqs).toEqual([{ q: 'Fees?', a: 'Ask us' }])
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
  it('uses the text, or the standard wording when it is empty', () => {
    expect(pick('Hello')).toBe('Hello')
    expect(pick('', 'standard')).toBe('standard')
    expect(pick(undefined, 'fallback')).toBe('fallback')
  })
})

describe('map address', () => {
  it('takes the address out of a pasted iframe and keeps ordinary Google share links', () => {
    const iframe = '<iframe src="https://www.google.com/maps/embed?pb=!1m18" width="600" height="450" loading="lazy"></iframe>'
    expect(cleanMapUrl(iframe)).toBe('https://www.google.com/maps/embed?pb=!1m18')
    expect(cleanMapUrl('https://maps.app.goo.gl/wciiBgChLTaB2wYH9')).toBe('https://maps.app.goo.gl/wciiBgChLTaB2wYH9')
    expect(cleanMapUrl('https://www.google.com/maps/place/Pune')).toContain('/maps/place/')
  })

  it('refuses anything that is not Google Maps', () => {
    expect(cleanMapUrl('https://evil.example/maps/embed')).toBe('')
    expect(cleanMapUrl('<iframe src="https://evil.example/x"></iframe>')).toBe('')
    expect(cleanMapUrl('javascript:alert(1)')).toBe('')
    expect(isMapEmbed('https://maps.app.goo.gl/x')).toBe(false)
  })
})
