import type { CourseSummaryResponse } from '@/api/courses'
import { CourseActions } from '@/components/course-actions'
import { formatMoney, hasCourseFee } from '@/lib/format'
import { siteImageUrl } from '@/lib/site-config'

/** One course as a card: photo or colour block, level, summary, stack, duration, fee, and the Buy and Inquiry buttons. */
export function CourseTile({ course, photo }: { course: CourseSummaryResponse; photo?: string }) {
  const tags = (course.technologyStack ?? '').split(/[,/|]/).map((t) => t.trim()).filter(Boolean).slice(0, 4)
  return (
    <article className="flex flex-col overflow-hidden rounded-xl border bg-card transition-all hover:-translate-y-1 hover:shadow-lg">
      {photo && <img src={siteImageUrl(photo)} alt="" loading="lazy" className="h-40 w-full object-cover" />}
      <div className={`p-5 text-primary-foreground ${photo ? 'bg-primary' : 'bg-gradient-to-br from-primary/80 to-primary/30'}`}>
        <span className="rounded-full bg-background/25 px-2 py-0.5 text-xs font-medium capitalize">{course.level.toLowerCase()}</span>
        <h2 className="mt-3 text-lg font-semibold leading-snug">{course.title}</h2>
      </div>
      <div className="flex flex-1 flex-col gap-3 p-5">
        {course.summary && <p className="line-clamp-3 text-sm text-muted-foreground">{course.summary}</p>}
        {tags.length > 0 && (
          <ul className="flex flex-wrap gap-1.5">
            {tags.map((tag) => (
              <li key={tag} className="rounded-md bg-muted px-2 py-0.5 text-xs">
                {tag}
              </li>
            ))}
          </ul>
        )}
        <p className="mt-auto flex items-center justify-between pt-2 text-sm text-muted-foreground">
          <span>{course.durationHours ? `${course.durationHours} hours` : ''}</span>
          <span className="text-base font-semibold text-foreground">
            {hasCourseFee(course.fee) ? formatMoney(course.fee ?? 0) : 'Fee on request'}
          </span>
        </p>
        <CourseActions course={course} />
      </div>
    </article>
  )
}
