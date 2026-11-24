import { ComponentType } from 'react';
import { ROUTES } from '@/constants/routes';
import { Bell, Calendar, Clock, Code2, LayoutDashboard, Trophy } from '@/components/ui/icons';

export type NavRole = 'HOD' | 'STAFF' | 'STUDENT';
type IconComponent = ComponentType<{ className?: string }>;

export interface NavItem {
  href: string;
  label: string;
  icon: IconComponent;
  /** Active only on this exact path (the dashboard), not its sub-routes. */
  exact?: boolean;
}

export interface NavSection {
  /** Forma section label, shown uppercase. Empty for the top-level dashboard link. */
  label: string;
  items: NavItem[];
  dropdown?: boolean;
  icon?: IconComponent;
}

/** Active when on the route or one of its sub-routes (e.g. a student portfolio). */
export const isActive = (pathname: string, href: string, exact?: boolean) =>
  exact ? pathname === href : pathname === href || pathname.startsWith(`${href}/`);

const DASHBOARD: NavItem = { href: ROUTES.DASHBOARD, label: 'Dashboard', icon: LayoutDashboard, exact: true };

/** Navigation labels and order stay consistent; Portfolio keeps its role-specific destination. */
export const buildNav = (role: NavRole): { main: NavSection[]; admin: NavItem[] } => ({
  main: [
    {
      label: '',
      items: [
        DASHBOARD,
        { href: ROUTES.LEETCODE, label: 'LeetCode', icon: Code2 },
        { href: role === 'STUDENT' ? ROUTES.PORTFOLIO : ROUTES.PORTFOLIO_REVIEW, label: 'Portfolio', icon: Trophy },
        { href: ROUTES.CALENDAR, label: 'Calendar', icon: Calendar },
      ],
    },
    {
      label: 'Schedule',
      dropdown: true,
      icon: Calendar,
      items: [
        { href: ROUTES.DEADLINE, label: 'Deadline', icon: Clock },
        { href: ROUTES.POLL, label: 'Poll', icon: Calendar },
      ],
    },
    { label: '', items: [{ href: ROUTES.NOTIFICATIONS, label: 'Notification', icon: Bell }] },
  ],
  admin: [],
});

interface PageMeta {
  title: string;
  subtitle: string;
}

/** Fixed page titles and one-line subtitles. Descriptive copy only — never data. */
const PAGES: Record<string, Partial<Record<NavRole | 'ALL', PageMeta>>> = {
  [ROUTES.DASHBOARD]: { ALL: { title: 'Dashboard', subtitle: 'Your department at a glance.' } },
  [ROUTES.CALENDAR]: { ALL: { title: 'Calendar', subtitle: 'Departmental events and government holidays.' } },
  [ROUTES.COURSES]: {
    STUDENT: { title: 'My Courses', subtitle: 'The courses you are enrolled in.' },
    STAFF: { title: 'My Courses', subtitle: 'The courses you teach and their class rosters.' },
    HOD: { title: 'Courses', subtitle: 'Courses by cohort, semester and section, enrollments and the subject catalog.' },
  },

  [ROUTES.FEES]: {
    STUDENT: { title: 'My Fee Details', subtitle: 'Charges, payments and balances on your account.' },
    ALL: { title: 'Fees', subtitle: 'Student charges, payments and fee structures.' },
  },
  [ROUTES.RECEIPTS]: {
    STUDENT: { title: 'My Receipts', subtitle: 'Receipts for payments recorded on your account.' },
    ALL: { title: 'Receipts', subtitle: 'Search and print payment receipts.' },
  },
  [ROUTES.EXAM_REGISTRATION]: {
    STUDENT: { title: 'Exam Registration', subtitle: 'Register for subjects in open exam periods.' },
    ALL: { title: 'Exam Registration', subtitle: 'Review registrations and manage exam periods.' },
  },

  [ROUTES.FEEDBACK]: {
    STUDENT: { title: 'Feedback', subtitle: 'Feedback forms shared with you.' },
    ALL: { title: 'Feedback', subtitle: 'Build feedback forms and review responses.' },
  },
  [ROUTES.NOTIFICATIONS]: { ALL: { title: 'Notifications', subtitle: 'Announcements and updates for you.' } },
  [ROUTES.AUDIT_LOG]: { ALL: { title: 'Audit Log', subtitle: 'A timestamped record of activity in the system.' } },
  [ROUTES.USERS]: { ALL: { title: 'Users', subtitle: 'Admin, faculty and student accounts.' } },
  [ROUTES.SETTINGS]: { ALL: { title: 'Account Settings', subtitle: 'Your account details, email and password.' } },
  [ROUTES.MY_GRADES]: { ALL: { title: 'My Grades', subtitle: 'Your marks across assessments.' } },
  [ROUTES.LEETCODE]: { ALL: { title: 'LeetCode', subtitle: 'Coding practice progress.' } },
  [ROUTES.PORTFOLIO]: { ALL: { title: 'My Portfolio', subtitle: 'Your achievements and activities.' } },
  [ROUTES.PORTFOLIO_REVIEW]: { ALL: { title: 'Portfolio Review', subtitle: 'Verify or return submitted portfolio entries.' } },
  [ROUTES.PORTFOLIO_ANALYTICS]: { ALL: { title: 'Portfolio Analytics', subtitle: 'Portfolio activity across students.' } },
  '/dashboard/schedule/deadline': {
    STUDENT: { title: 'Deadlines', subtitle: 'Upcoming deadlines shared with you.' },
    ALL: { title: 'Deadlines', subtitle: 'Set and manage deadlines for students.' },
  },
  '/dashboard/schedule/poll': {
    STUDENT: { title: 'Polls', subtitle: 'Polls shared with you.' },
    ALL: { title: 'Polls', subtitle: 'Create polls and see responses.' },
  },
};

const DYNAMIC: { prefix: string; meta: PageMeta }[] = [
  { prefix: '/dashboard/portfolio/students/', meta: { title: 'Student Portfolio', subtitle: 'A student’s portfolio entries.' } },
];

export const getPageMeta = (pathname: string, role: NavRole): PageMeta => {
  const entry = PAGES[pathname];
  if (entry) {
    const meta = entry[role] ?? entry.ALL;
    if (meta) return meta;
  }
  const dynamic = DYNAMIC.find((d) => pathname.startsWith(d.prefix));
  if (dynamic) return dynamic.meta;
  const last = pathname.split('/').filter((part) => part && !/^\d+$/.test(part)).pop() ?? 'dashboard';
  return { title: last.split('-').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' '), subtitle: '' };
};

/** The sidebar section a route belongs to, used as the top-bar breadcrumb. */
export const getSectionLabel = (pathname: string, role: NavRole): string => {
  const { main, admin } = buildNav(role);
  const best = [...main.flatMap((s) => s.items.map((item) => ({ item, label: s.label }))), ...admin.map((item) => ({ item, label: 'Administration' }))]
    .filter(({ item }) => isActive(pathname, item.href, item.exact))
    .sort((a, b) => b.item.href.length - a.item.href.length)[0];
  return best?.label || 'Department workspace';
};

export const roleOf = (hasRole: (role: 'ROLE_HOD' | 'ROLE_STAFF' | 'ROLE_STUDENT') => boolean): NavRole =>
  hasRole('ROLE_HOD') ? 'HOD' : hasRole('ROLE_STAFF') ? 'STAFF' : 'STUDENT';

export const ROLE_LABEL: Record<NavRole, string> = { HOD: 'HOD', STAFF: 'Faculty', STUDENT: 'Student' };

export const initialsOf = (name: string | undefined) =>
  (name ?? '')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('') || '?';
