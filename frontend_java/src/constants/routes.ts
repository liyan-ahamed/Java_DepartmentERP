export const ROUTES = {
  HOME: '/',
  LOGIN: '/login',
  DASHBOARD: '/dashboard',
  CALENDAR: '/dashboard/calendar',
  COURSES: '/dashboard/courses',
  /** Old route — redirects to Courses → Enrollments. */
  COURSE_ENROLLMENT: '/dashboard/course-enrollment',
  /** Retired — redirects to Courses. */
  LESSON_PLAN: '/dashboard/lesson-plan',
  FEES: '/dashboard/fees',
  RECEIPTS: '/dashboard/receipts',
  EXAM_REGISTRATION: '/dashboard/exam-registration',
  FEEDBACK: '/dashboard/feedback',
  NOTIFICATIONS: '/dashboard/notifications',
  AUDIT_LOG: '/dashboard/audit-log',
  USERS: '/dashboard/users',
  /** Old route — redirects to Courses → Subject Catalog. */
  SUBJECTS: '/dashboard/subjects',
  /** Old route — redirects to Courses → Class Courses. */
  SUBJECT_OFFERINGS: '/dashboard/subject-offerings',
  MY_GRADES: '/dashboard/my-grades',
  DEADLINE: '/dashboard/schedule/deadline',
  POLL: '/dashboard/schedule/poll',
  LEETCODE: '/dashboard/leetcode',
  PORTFOLIO: '/dashboard/portfolio',
  PORTFOLIO_REVIEW: '/dashboard/portfolio/review',
  PORTFOLIO_ANALYTICS: '/dashboard/portfolio/analytics',
  PORTFOLIO_STUDENT: (studentId: number) => `/dashboard/portfolio/students/${studentId}`,
  SETTINGS: '/dashboard/settings',
} as const;
