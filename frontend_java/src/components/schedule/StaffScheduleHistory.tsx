'use client';

import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/Table';
import { useStaffPollResponses, useStaffScheduleHistory } from '@/hooks/useSchedule';
import { StudentScheduleType } from '@/types/schedule';
import { getApiErrorMessage } from '@/lib/api-error';
import { dateLabel } from '@/lib/calendar-dates';

const timestamp = (value: string) => new Date(value).toLocaleString('en-IN', {
  day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
});

function PollResponses({ id }: { id: number }) {
  const responses = useStaffPollResponses(id);
  const rows = responses.data?.pages.flatMap((page) => page.content) ?? [];
  return <section aria-label="Student poll responses" className="border border-line rounded-lg overflow-hidden mt-3">
    {responses.isPending ? <p className="p-4 text-sm text-muted" role="status">Loading responses…</p> : responses.isError && !responses.data ?
      <p className="p-4 text-sm text-danger" role="alert">{getApiErrorMessage(responses.error, 'Unable to load responses.')} <button className="underline" onClick={() => responses.refetch()}>Retry</button></p> : <>
      <p className="px-4 py-3 text-xs text-muted">Showing {rows.length} of {responses.data?.pages[0].total_elements ?? 0} responses</p>
      {rows.length === 0 ? <p className="p-4 text-sm text-muted">No students have responded yet.</p> : <Table>
        <TableHeader><TableRow><TableHead>Student</TableHead><TableHead>Class</TableHead><TableHead>Response</TableHead><TableHead>Submitted</TableHead></TableRow></TableHeader>
        <TableBody>{rows.map((row) => <TableRow key={row.studentId}>
          <TableCell><span className="block font-medium">{row.studentName}</span><span className="text-xs text-muted">{row.registerNumber}</span></TableCell>
          <TableCell className="text-xs">{row.targetClass}</TableCell><TableCell>{row.selectedOption}</TableCell><TableCell className="text-xs whitespace-nowrap">{timestamp(row.respondedAt)}</TableCell>
        </TableRow>)}</TableBody>
      </Table>}
      {responses.isError && <p className="px-4 py-2 text-sm text-danger" role="alert">Could not update responses. Please retry.</p>}
      {responses.hasNextPage && <div className="p-3 border-t border-line"><Button size="sm" variant="outline" onClick={() => responses.fetchNextPage()} isLoading={responses.isFetchingNextPage}>More responses</Button></div>}
    </>}
  </section>;
}

export function StaffScheduleHistory({ type }: { type: StudentScheduleType }) {
  const history = useStaffScheduleHistory(type);
  const [openPoll, setOpenPoll] = useState<number | null>(null);
  const rows = history.data?.pages.flatMap((page) => page.content) ?? [];
  const label = type === 'POLL' ? 'Polls' : 'Deadlines';
  return <Card>
    <CardHeader><div className="flex items-center justify-between gap-3"><div><CardTitle>Your {label}</CardTitle><p className="text-xs text-muted mt-1">Latest first · five at a time · created by you</p></div><Button size="sm" variant="outline" onClick={() => history.refetch()} isLoading={history.isFetching && !history.isFetchingNextPage}>Refresh</Button></div></CardHeader>
    <CardContent className="p-0">
      {history.isPending ? <div className="p-10 flex justify-center"><Spinner size="lg" /></div> : history.isError && !history.data ?
        <p role="alert" className="p-5 text-sm text-danger">{getApiErrorMessage(history.error, 'Unable to load your schedule history.')} <button className="underline" onClick={() => history.refetch()}>Retry</button></p> : rows.length === 0 ?
          <p className="p-8 text-center text-sm text-muted">You have not created any {label.toLowerCase()} yet.</p> : <>
            <div className="divide-y divide-line">{rows.map((item) => {
              const schedule = item.schedule;
              return <article key={schedule.id} className="p-5 space-y-3" aria-label={schedule.title}>
                <h3 className="text-sm font-[550]">{schedule.title}</h3>
                {schedule.details && <p className="text-sm text-muted whitespace-pre-wrap">{schedule.details}</p>}
                <p className="text-xs text-muted">Created: {timestamp(schedule.created_at)}</p>
                {schedule.due_date && <p className="text-sm">Scheduled: {dateLabel(schedule.due_date)}{schedule.due_time ? ` at ${schedule.due_time.slice(0, 5)}` : ' · All day'}</p>}
                {item.targetClasses.length > 0 && <p className="text-sm break-words"><span className="font-medium">Classes: </span>{item.targetClasses.join('; ')}</p>}
                {item.targetStudents.length > 0 && <p className="text-sm break-words"><span className="font-medium">Students: </span>{item.targetStudents.join('; ')}</p>}
                {type === 'POLL' && <>
                  <div className="flex gap-x-5 gap-y-2 flex-wrap text-sm"><span>Assigned students: <strong>{item.assignedCount}</strong></span><span>Responded: <strong>{item.respondedCount}</strong></span></div>
                  <p className="text-xs text-muted">{schedule.options.map((option, i) => `${i + 1}. ${option}`).join(' · ')}</p>
                  <Button size="sm" variant="outline" aria-expanded={openPoll === schedule.id} aria-controls={`responses-${schedule.id}`} onClick={() => setOpenPoll(openPoll === schedule.id ? null : schedule.id)}>{openPoll === schedule.id ? 'Hide responses' : 'View responses'}</Button>
                  {openPoll === schedule.id && <div id={`responses-${schedule.id}`}><PollResponses id={schedule.id} /></div>}
                </>}
              </article>;
            })}</div>
            {history.isError && <p role="alert" className="px-5 py-2 text-sm text-danger">{getApiErrorMessage(history.error, 'Unable to load more history. Please retry.')}</p>}
            <div className="px-5 py-4 border-t border-line flex justify-between items-center gap-3"><span className="text-xs text-muted">Showing {rows.length} of {history.data?.pages[0].total_elements ?? 0}</span>{history.hasNextPage && <Button size="sm" variant="outline" onClick={() => history.fetchNextPage()} isLoading={history.isFetchingNextPage}>More</Button>}</div>
          </>}
    </CardContent>
  </Card>;
}
