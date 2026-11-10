import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { SearchIcon } from 'lucide-react'

import { getAllPublicCourses } from '@/api/public-site'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { useSite } from '@/lib/site-config'
import { CourseTile } from '@/components/course-tile'
import { SiteShell } from '@/components/site-shell'

/**
 * The public course catalog at /courses: every published course with its fee, a search box, and the Buy and Inquiry
 * buttons. Open to anyone; the footer's Courses link comes here.
 */
export function PublicCoursesPage() {
  const site = useSite()
  const query = useQuery({ queryKey: ['public-courses', 'all'], queryFn: getAllPublicCourses, staleTime: 5 * 60_000 })
  const [text, setText] = useState('')
  const [level, setLevel] = useState('ALL')

  const all = useMemo(() => query.data?.content ?? [], [query.data])
  const levels = useMemo(() => Array.from(new Set(all.map((c) => c.level))).sort(), [all])
  const shown = useMemo(() => {
    const needle = text.trim().toLowerCase()
    return all.filter(
      (c) =>
        (level === 'ALL' || c.level === level) &&
        (!needle || `${c.title} ${c.summary ?? ''} ${c.technologyStack ?? ''}`.toLowerCase().includes(needle)),
    )
  }, [all, text, level])

  return (
    <SiteShell
      title="Our courses"
      intro="Pick a course, see what it covers and what it costs, then buy it online or send us an inquiry."
    >
          <div className="mb-8 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-center">
            <div className="relative w-full sm:max-w-sm">
              <SearchIcon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                className="pl-9"
                placeholder="Search courses"
                aria-label="Search courses"
                value={text}
                onChange={(event) => setText(event.target.value)}
              />
            </div>
            {levels.length > 1 && (
              <div className="flex flex-wrap justify-center gap-2" role="group" aria-label="Level">
                {['ALL', ...levels].map((value) => (
                  <Button
                    key={value}
                    type="button"
                    size="sm"
                    variant={level === value ? 'default' : 'outline'}
                    onClick={() => setLevel(value)}
                    className="capitalize"
                  >
                    {value === 'ALL' ? 'All levels' : value.toLowerCase()}
                  </Button>
                ))}
              </div>
            )}
          </div>

          {query.isLoading && (
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {[0, 1, 2].map((i) => (
                <Skeleton key={i} className="h-72" />
              ))}
            </div>
          )}
          {query.isError && <p className="text-center text-sm text-muted-foreground">The courses could not be loaded. Please try again.</p>}
          {query.isSuccess && shown.length === 0 && (
            <p className="text-center text-sm text-muted-foreground">
              {all.length === 0 ? 'No courses are open right now.' : 'No course matches your search.'}
            </p>
          )}

          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {shown.map((course) => (
              <CourseTile key={course.id} course={course} photo={site.courseCards.images[String(course.id)]} />
            ))}
          </div>
    </SiteShell>
  )
}
