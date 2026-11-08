import { describe, expect, it } from 'vitest'

import { DEFAULT_UI_CONFIG, parseUiConfig } from './ui-config'

describe('parseUiConfig', () => {
  it.each([[null], [undefined], [''], ['not json'], ['[]'], ['42']])('falls back to defaults for %j', (text) => {
    const config = parseUiConfig(text)
    expect(config.theme.defaultMode).toBe('system')
    expect(config.theme.radius).toBe(DEFAULT_UI_CONFIG.theme.radius)
    expect(config.menus).toEqual({})
    expect(config.terms).toEqual({})
  })

  it('keeps sane values and clamps the rest', () => {
    const config = parseUiConfig(JSON.stringify({ theme: { defaultMode: 'dark', radius: 9, fontScale: 10, bgStrength: 500 } }))
    expect(config.theme.defaultMode).toBe('dark')
    expect(config.theme.radius).toBe(1.5)
    expect(config.theme.fontScale).toBe(85)
    expect(config.theme.bgStrength).toBe(100)
  })

  it('accepts only a six-digit hex colour', () => {
    expect(parseUiConfig(JSON.stringify({ theme: { primary: '#2563eb' } })).theme.primary).toBe('#2563eb')
    expect(parseUiConfig(JSON.stringify({ theme: { primary: 'red' } })).theme.primary).toBe('')
    expect(parseUiConfig(JSON.stringify({ theme: { primary: '#fff' } })).theme.primary).toBe('')
  })

  it('ignores an unknown theme mode', () => {
    expect(parseUiConfig(JSON.stringify({ theme: { defaultMode: 'purple' } })).theme.defaultMode).toBe('system')
  })

  it('keeps only strings in a role menu and drops blank labels', () => {
    const config = parseUiConfig(
      JSON.stringify({
        menus: {
          STUDENT: {
            hidden: ['/app/files', 7, null],
            order: ['/app', '/app/courses'],
            labels: { '/app/files': 'Documents', '/app/x': '  ', '': 'no key' },
          },
        },
      }),
    )
    expect(config.menus.STUDENT?.hidden).toEqual(['/app/files'])
    expect(config.menus.STUDENT?.order).toEqual(['/app', '/app/courses'])
    expect(config.menus.STUDENT?.labels).toEqual({ '/app/files': 'Documents' })
  })
})
