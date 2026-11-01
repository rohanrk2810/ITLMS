import { describe, expect, it } from 'vitest'

import { youtubeIdOf } from './youtube'

describe('youtubeIdOf', () => {
  it.each([
    ['https://www.youtube.com/watch?v=dQw4w9WgXcQ', 'dQw4w9WgXcQ'],
    ['https://youtu.be/dQw4w9WgXcQ', 'dQw4w9WgXcQ'],
    ['https://m.youtube.com/watch?v=abc123', 'abc123'],
    ['https://www.youtube.com/embed/abc-123_X', 'abc-123_X'],
    ['https://www.youtube.com/shorts/abcDEF12345', 'abcDEF12345'],
    ['https://www.youtube.com/live/liveID99', 'liveID99'],
    ['youtube.com/watch?v=noScheme1', 'noScheme1'],
    ['  https://youtu.be/spaced99  ', 'spaced99'],
  ])('%s -> %s', (link, id) => {
    expect(youtubeIdOf(link)).toBe(id)
  })

  it('returns null for other sites and for things that are not links', () => {
    expect(youtubeIdOf('https://vimeo.com/12345')).toBeNull()
    expect(youtubeIdOf('https://evil.example/youtube.com/watch?v=abc')).toBeNull()
    expect(youtubeIdOf('https://www.youtube.com/')).toBeNull()
    expect(youtubeIdOf('not a link at all')).toBeNull()
  })
})
