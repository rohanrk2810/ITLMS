import { describe, expect, it } from 'vitest'

import type { Branding } from '@/api/branding'

import { type Letterhead, type Report, escapeHtml, letterheadOf, pickColumns, reportHtml } from './report'

const letterhead: Letterhead = {
  name: 'GH Raisoni College of Engineering and Management',
  tagline: 'Jalgaon',
  lines: ['Jalgaon, Maharashtra', '0257 1234567  |  info@example.edu'],
  logoUrl: 'https://api.example.edu/api/public/branding/logo',
}

const report: Report = {
  title: 'Batch attendance',
  fileName: 'attendance-b1',
  info: [['Batch', 'JFS-B1'], ['Course', 'Java'], ['Trainer', 'Ravi K']],
  header: ['Student', 'Attendance %'],
  rows: [['Asha Patil', 90], ['Ravi Kale', 30]],
}

describe('reportHtml (the printed page)', () => {
  const html = reportHtml(report, letterhead, '1 November 2026')

  it("starts with the institute's own letterhead: logo, name, tagline and address", () => {
    expect(html).toContain('GH Raisoni College of Engineering and Management')
    expect(html).toContain('Jalgaon')
    expect(html).toContain('info@example.edu')
    expect(html).toContain('<img class="logo" src="https://api.example.edu/api/public/branding/logo"')
    // The letterhead comes before the title in the page itself (the <title> tag is not the page).
    expect(html.indexOf('class="name"')).toBeLessThan(html.indexOf('class="title"'))
  })

  it('has the heavy rule, the centred title and the facts as a small table of headings over values', () => {
    expect(html).toContain('<hr class="rule" />')
    expect(html).toContain('<h1 class="title">Batch attendance</h1>')
    expect(html).toContain('<th>Batch</th><th>Course</th><th>Trainer</th>')
    expect(html).toContain('<td>JFS-B1</td><td>Java</td><td>Ravi K</td>')
  })

  it('prints the data with a repeating header, and numbers apart from text', () => {
    expect(html).toContain('<thead><tr><th>Student</th><th>Attendance %</th></tr></thead>')
    expect(html).toContain('<td>Asha Patil</td><td class="num">90</td>')
    expect(html).toContain('2 row(s). Made on 1 November 2026.')
  })

  it('puts the logo behind the table as a watermark, and none when there is no logo', () => {
    expect(html).toContain('<img class="watermark" src="https://api.example.edu/api/public/branding/logo"')
    expect(reportHtml(report, { ...letterhead, logoUrl: null }, 'x')).not.toContain('watermark" src')
  })

  it('breaks more than six facts over several small tables', () => {
    const many: Report = { ...report, info: Array.from({ length: 8 }, (_, i) => [`K${i}`, `V${i}`] as [string, string]) }
    expect((reportHtml(many, letterhead, 'x').match(/<table class="info">/g) ?? []).length).toBe(2)
  })

  it('shows a section row across the table, not as a value row', () => {
    const sections: Report = { ...report, header: ['Item', 'Value'], rows: [['# Attendance', ''], ['Overall %', 80]] }
    const out = reportHtml(sections, letterhead, 'x')
    expect(out).toContain('<tr class="section"><td colspan="2">Attendance</td></tr>')
    expect(out).toContain('1 row(s).') // the section heading is not a row of data
  })

  it('a few columns print upright; a wide table prints across the page with a smaller type', () => {
    expect(html).toContain('size: A4;')
    expect(html).toContain('font-size: 9.5pt;')
    const wide = (n: number): Report => ({
      ...report,
      header: Array.from({ length: n }, (_, i) => `C${i}`),
      rows: [Array.from({ length: n }, (_, i) => i)],
    })
    const nine = reportHtml(wide(9), letterhead, 'x')
    expect(nine).toContain('size: A4 landscape;')
    expect(nine).toContain('overflow-wrap: break-word;')
    expect(nine).toContain('font-size: 8.5pt;')
    expect(reportHtml(wide(20), letterhead, 'x')).toContain('font-size: 7.5pt;')
    expect(reportHtml(wide(8), letterhead, 'x')).toContain('size: A4;')
  })

  it('leaves out the logo and the tagline when the institute has none', () => {
    const plain = reportHtml(report, { name: 'Plain Institute', tagline: null, lines: [], logoUrl: null }, 'x')
    expect(plain).not.toContain('<img')
    expect(plain).not.toContain('class="tagline"')
    expect(plain).toContain('Plain Institute')
  })

  it('a name or note with markup prints as text and cannot run', () => {
    const hostile: Report = { ...report, rows: [['<script>alert(1)</script>', 1]], info: [['Search', '"><img src=x onerror=alert(1)>']] }
    const out = reportHtml(hostile, { ...letterhead, name: '<b>Bad</b> Institute' }, 'x')
    expect(out).not.toContain('<script>')
    expect(out).not.toContain('<img src=x')
    expect(out).not.toContain('<b>Bad</b>')
    expect(out).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
  })
})

describe('escapeHtml', () => {
  it('escapes the five characters that matter, and shows nothing for a missing value', () => {
    expect(escapeHtml(`<a href="x" title='y'>&</a>`)).toBe('&lt;a href=&quot;x&quot; title=&#39;y&#39;&gt;&amp;&lt;/a&gt;')
    expect(escapeHtml(null)).toBe('')
    expect(escapeHtml(undefined)).toBe('')
    expect(escapeHtml(0)).toBe('0')
  })
})

describe('letterheadOf', () => {
  const branding = {
    name: ' Test Institute ', tagline: 'Learn well', address: '1 Main Road', contactPhone: '0257 111', contactEmail: 'a@b.edu', website: null,
  } as unknown as Branding

  it('takes the name, tagline, address and contact line from the branding settings', () => {
    const l = letterheadOf(branding, 'https://x/logo', 'Fallback')
    expect(l.name).toBe('Test Institute')
    expect(l.tagline).toBe('Learn well')
    expect(l.lines).toEqual(['1 Main Road', '0257 111  |  a@b.edu'])
    expect(l.logoUrl).toBe('https://x/logo')
  })

  it('falls back to a plain name when the settings have not loaded', () => {
    const l = letterheadOf(undefined, null, 'Learning Management System')
    expect(l).toEqual({ name: 'Learning Management System', tagline: null, lines: [], logoUrl: null })
  })
})

describe('pickColumns', () => {
  const base: Report = {
    title: 'Leads', info: [], fileName: 'leads',
    header: ['Name', 'Phone', 'Email', 'Source'],
    rows: [['Asha', '9800000001', 'a@x.test', 'WALK_IN'], ['Ravi', '9800000002', 'r@x.test', 'WEBSITE']],
  }

  it('keeps only the chosen columns, in their original order', () => {
    const out = pickColumns(base, [3, 0, 1])
    expect(out.header).toEqual(['Name', 'Phone', 'Source'])
    expect(out.rows).toEqual([['Asha', '9800000001', 'WALK_IN'], ['Ravi', '9800000002', 'WEBSITE']])
  })

  it('leaves the title and facts alone', () => {
    expect(pickColumns(base, [0]).title).toBe('Leads')
  })

  it('keeps the heading of a section row when the first column is dropped', () => {
    const sectioned: Report = { ...base, header: ['Item', 'Value'], rows: [['# Attendance', ''], ['Overall %', 80]] }
    expect(pickColumns(sectioned, [1]).rows).toEqual([['# Attendance'], [80]])
  })
})
