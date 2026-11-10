import { useQuery } from '@tanstack/react-query'

import { getPublicCourses } from '@/api/public-site'
import { EnquiryForm } from '@/pages/home/enquiry-form'

/** The enquiry form with the list of courses filled in, and optionally a first line for the message. */
export function EnquiryBlock({ message }: { message?: string }) {
  const courses = useQuery({ queryKey: ['public-courses'], queryFn: getPublicCourses, staleTime: 5 * 60_000 })
  return <EnquiryForm courses={courses.data?.content ?? []} defaultMessage={message} />
}
