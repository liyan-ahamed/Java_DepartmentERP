'use client';

import Link from 'next/link';
import { ReactNode } from 'react';
import { PageContainer } from '@/components/common/PageContainer';
import { RequireRole } from '@/components/common/RequireRole';
import { MetricCard, MetricGrid } from '@/components/ui/MetricCard';
import { Badge } from '@/components/ui/Badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/Table';
import { Bell, Calendar, GraduationCap, Users } from '@/components/ui/icons';
import { ErrorBanner } from '@/components/gradebook/shared';
import { useDashboardSummary } from '@/hooks/useDashboardQuery';
import { useUnreadCount } from '@/hooks/useNotifications';
import { useCalendar } from '@/hooks/useCalendar';
import { useStudentSchedules } from '@/hooks/useSchedule';
import { useAuth } from '@/contexts/auth-context';
import { dashboardGreeting } from '@/components/dashboard/greeting';
import { addDays, dateLabel, localDate, upcomingDeadlines, upcomingEntries } from '@/lib/calendar-dates';
import { ROUTES } from '@/constants/routes';

function OverviewPanel({ title, href, hint, children }: { title: string; href: string; hint: string; children: ReactNode }) {
  return <section className="panel">
    <div className="panel-header"><div><h2>{title}</h2><p>{hint}</p></div><Link href={href} className="text-[12px] text-accent shrink-0 hover:underline">View all</Link></div>
    {children}
  </section>;
}

function DepartmentOverview() {
  const { user } = useAuth();
  const summary = useDashboardSummary();
  const unread = useUnreadCount();
  const today = localDate();
  const calendar = useCalendar(today, addDays(today, 90));
  const deadlines = useStudentSchedules('DEADLINE');
  const events = upcomingEntries(calendar.data?.entries ?? [], today).slice(0, 6);
  const upcoming = upcomingDeadlines(deadlines.data ?? []).slice(0, 6);
  const years = summary.data?.year_wise_students.toSorted((a, b) => a.year - b.year) ?? [];
  const sections = summary.data?.section_wise_students ?? [];
  const metric = (value: number | undefined) => summary.isLoading ? '…' : summary.isError || value === undefined ? '—' : value.toLocaleString();
  const total = summary.data?.total_students ?? 0;

  return <PageContainer eyebrow="Your department, connected" title={dashboardGreeting(user?.name)} description="People, calendar dates and upcoming deadlines across your ERP.">
    {summary.isError && <div className="mb-5"><ErrorBanner error={summary.error} fallback="Unable to load department totals." /></div>}
    <MetricGrid>
      <MetricCard title="Total Students" value={metric(summary.data?.total_students)} icon={<GraduationCap />} subtitle="Active students in active batches" />
      <MetricCard title="Total Staff" value={metric(summary.data?.total_staff)} icon={<Users />} subtitle="Active staff accounts" />
      <MetricCard title="Academic Years" value={metric(summary.data ? years.length : undefined)} icon={<Calendar />} subtitle="Years with enrolled students" />
      <MetricCard title="Unread Notifications" value={unread.isLoading ? '…' : unread.isError ? '—' : (unread.data ?? 0).toLocaleString()} icon={<Bell />} subtitle={unread.isError ? 'Count unavailable' : 'Announcements and updates'} href={ROUTES.NOTIFICATIONS} />
    </MetricGrid>

    <div className="grid grid-cols-1 xl:grid-cols-2 gap-[21px] mb-[21px] items-start">
      <OverviewPanel title="Upcoming Events & Holidays" href={ROUTES.CALENDAR} hint="Today and the next 90 days">
        {calendar.isLoading ? <p className="p-5 text-[13px] text-muted">Loading calendar…</p> : calendar.isError ? <div className="p-5"><ErrorBanner error={calendar.error} fallback="Unable to load upcoming calendar dates." /></div> : <>
          {events.length === 0 ? <p className="p-5 text-[13px] text-muted">No upcoming events or holidays in this period.</p> : <ul className="divide-y divide-line border-t border-line">
            {events.map((entry) => <li key={entry.id} className="px-5 py-4 flex items-start gap-3">
              <Calendar className="text-muted mt-0.5 shrink-0" />
              <div className="min-w-0 flex-1"><p className="text-[13px] font-medium break-words">{entry.title}</p><p className="text-[12px] text-muted mt-1">{dateLabel(entry.startDate)}{entry.endDate !== entry.startDate ? ` – ${dateLabel(entry.endDate)}` : ''}{entry.government ? ' · Government holiday' : ''}</p></div>
              <Badge variant={entry.holiday ? 'warning' : 'info'}>{entry.holiday ? 'Holiday' : 'Event'}</Badge>
            </li>)}
          </ul>}
          {Array.from(new Set([Number(today.slice(0, 4)), Number(addDays(today, 90).slice(0, 4))])).filter((year) => !calendar.data?.governmentYearsAvailable.includes(year)).map((year) => <p key={year} className="px-5 pb-4 text-[12px] text-muted">Government holiday data for {year} is not available yet.</p>)}
        </>}
      </OverviewPanel>
      <OverviewPanel title="Upcoming Deadlines" href={ROUTES.DEADLINE} hint="Deadlines available to your account">
        {deadlines.isLoading ? <p className="p-5 text-[13px] text-muted">Loading deadlines…</p> : deadlines.isError ? <div className="p-5"><ErrorBanner error={deadlines.error} fallback="Unable to load upcoming deadlines." /></div> : upcoming.length === 0 ? <p className="p-5 text-[13px] text-muted">No upcoming deadlines.</p> : <ul className="divide-y divide-line border-t border-line">
          {upcoming.map((deadline) => <li key={deadline.id} className="px-5 py-4"><Link href={ROUTES.DEADLINE} className="text-[13px] font-medium hover:underline break-words">{deadline.title}</Link><p className="text-[12px] text-muted mt-1">{dateLabel(deadline.due_date!)}{deadline.due_time ? ` · ${deadline.due_time.slice(0, 5)}` : ' · All day'} · {deadline.created_by_name}</p></li>)}
        </ul>}
      </OverviewPanel>
    </div>

    <div className="grid grid-cols-1 lg:grid-cols-[1fr_1.45fr] gap-[21px] items-start">
      <section className="panel">
        <div className="panel-header"><div><h2>Students by Academic Year</h2><p>Active students across the department.</p></div></div>
        {summary.isLoading ? <p className="p-5 text-[13px] text-muted">Loading distribution…</p> : summary.isError ? <p className="p-5 text-[13px] text-muted">Distribution unavailable.</p> : years.length === 0 ? <p className="p-5 text-[13px] text-muted">No student distribution available.</p> : <ul className="divide-y divide-line border-t border-line">
          {years.map((year) => <li key={year.year} className="px-5 py-4"><div className="flex justify-between text-[13px] mb-2"><span>Year {year.year}</span><strong className="font-medium">{year.student_count.toLocaleString()}</strong></div><div className="h-2 bg-soft rounded-full overflow-hidden" role="img" aria-label={`Year ${year.year}: ${year.student_count} students`}><div className="h-full bg-accent rounded-full" style={{ width: `${total ? year.student_count / total * 100 : 0}%` }} /></div></li>)}
        </ul>}
      </section>
      <section className="panel">
        <div className="panel-header"><div><h2>Students by Section</h2><p>Section strength across active batches.</p></div></div>
        {summary.isLoading ? <p className="p-5 text-[13px] text-muted">Loading sections…</p> : summary.isError ? <p className="p-5 text-[13px] text-muted">Section data unavailable.</p> : sections.length === 0 ? <p className="p-5 text-[13px] text-muted">No section data available.</p> : <Table>
          <TableHeader><TableRow><TableHead>Section</TableHead><TableHead>Batch</TableHead><TableHead className="text-right">Students</TableHead></TableRow></TableHeader>
          <TableBody>{sections.map((section) => <TableRow key={section.section_id}><TableCell className="font-medium">{section.section_name}</TableCell><TableCell>{section.batch_name}</TableCell><TableCell className="text-right tabular-nums">{section.student_count.toLocaleString()}</TableCell></TableRow>)}</TableBody>
        </Table>}
      </section>
    </div>
  </PageContainer>;
}

export default function DashboardPage() {
  return <RequireRole roles={['ROLE_HOD', 'ROLE_STAFF', 'ROLE_STUDENT']}><DepartmentOverview /></RequireRole>;
}
