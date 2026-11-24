'use client';

import { useState } from 'react';
import { PageContainer } from '@/components/common/PageContainer';
import { RequireRole } from '@/components/common/RequireRole';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { ChevronLeft, ChevronRight } from '@/components/ui/icons';
import { CalendarEntryForm } from '@/components/calendar/CalendarEntryForm';
import { ErrorBanner } from '@/components/gradebook/shared';
import { useAuth } from '@/contexts/auth-context';
import { useCalendar } from '@/hooks/useCalendar';
import { dateLabel, dateValue, entriesOn, localDate, monthDays, shiftMonth } from '@/lib/calendar-dates';

function DepartmentCalendar() {
  const { hasRole } = useAuth();
  const canCreate = hasRole('ROLE_STAFF');
  const today = localDate();
  const [month, setMonth] = useState(() => today.slice(0, 7));
  const [selected, setSelected] = useState(today);
  const [formDate, setFormDate] = useState<string | null>(null);
  const days = monthDays(month);
  const calendar = useCalendar(days[0], days[days.length - 1]);
  const entries = calendar.data?.entries ?? [];
  const selectedEntries = entriesOn(entries, selected);
  const heading = dateValue(`${month}-01`).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });
  const changeMonth = (value: string) => { setMonth(value); setSelected(`${value}-01`); };
  const choose = (date: string) => {
    setSelected(date);
    if (date.slice(0, 7) !== month) setMonth(date.slice(0, 7));
    if (canCreate) setFormDate(date);
  };
  return (
    <PageContainer description={canCreate ? 'Select a date to add an event or mark a holiday.' : 'Government holidays and departmental events. Calendar entries are managed by staff.'}>
      <section className="panel mb-5" aria-label="Monthly calendar">
        <div className="panel-header flex-wrap gap-3">
          <h2 aria-live="polite">{heading}</h2>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" aria-label="Previous month" onClick={() => changeMonth(shiftMonth(month, -1))}><ChevronLeft /></Button>
            <Button variant="outline" size="sm" onClick={() => { setMonth(today.slice(0, 7)); setSelected(today); }}>Today</Button>
            <Button variant="outline" size="sm" aria-label="Next month" onClick={() => changeMonth(shiftMonth(month, 1))}><ChevronRight /></Button>
          </div>
        </div>
        <div className="px-5 py-3 flex flex-wrap gap-3 text-[12px] text-muted border-t border-line">
          <span><span className="inline-block w-2 h-2 rounded-full bg-accent mr-1.5" />Event</span>
          <span><span className="inline-block w-2 h-2 rounded-full bg-warn mr-1.5" />Holiday · no classes</span>
          <span>Government holidays: India and Tamil Nadu</span>
          {calendar.isFetching && <span role="status">Updating calendar…</span>}
        </div>
        {calendar.isError && <div className="p-4"><ErrorBanner error={calendar.error} fallback="Unable to load the calendar." /><Button size="sm" variant="outline" onClick={() => calendar.refetch()}>Retry</Button></div>}
        {!calendar.isLoading && !calendar.isError && !calendar.data?.governmentYearsAvailable.includes(Number(month.slice(0, 4))) && (
          <p className="px-5 pb-3 text-[12px] text-muted" role="status">Government holiday data for {month.slice(0, 4)} is not available yet. Department entries are still shown.</p>
        )}
        <div className="overflow-x-auto">
          <div className="min-w-[700px]">
            <div className="grid grid-cols-7 border-y border-line bg-soft">
              {['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((day) => <div key={day} className="px-3 py-2 text-[11px] font-medium text-muted">{day}</div>)}
            </div>
            <div className="grid grid-cols-7">
              {days.map((date) => {
                const rows = entriesOn(entries, date);
                const outside = date.slice(0, 7) !== month;
                return (
                  <button key={date} type="button" onClick={() => choose(date)} aria-pressed={selected === date}
                    aria-label={`${dateLabel(date)}, ${rows.length} ${rows.length === 1 ? 'entry' : 'entries'}${canCreate ? ', add calendar entry' : ''}`}
                    className={`min-h-[130px] p-2.5 border-b border-r border-line text-left flex flex-col gap-1.5 hover:bg-soft focus-visible:outline-2 focus-visible:outline-accent focus-visible:-outline-offset-2 ${outside ? 'bg-soft text-muted' : 'text-fg'} ${selected === date ? 'ring-2 ring-inset ring-accent' : ''}`}>
                    <span aria-current={date === today ? 'date' : undefined} className={`inline-flex w-6 h-6 items-center justify-center text-[12px] rounded-full ${date === today ? 'bg-accent text-white' : ''}`}>{Number(date.slice(-2))}</span>
                    {rows.slice(0, 3).map((entry) => <span key={entry.id} title={entry.title} className={`block w-full truncate text-[11px] rounded px-1.5 py-0.5 ${entry.holiday ? 'bg-warn-soft text-warn' : 'bg-accent-soft text-accent'}`}>{entry.holiday ? 'Holiday: ' : ''}{entry.title}</span>)}
                    {rows.length > 3 && <span className="text-[10px] text-muted">+{rows.length - 3} more</span>}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      </section>
      <section className="panel" aria-label="Selected date entries">
        <div className="panel-header flex-wrap gap-2"><h2>{dateLabel(selected)}</h2>{canCreate && <Button size="sm" onClick={() => setFormDate(selected)}>Add entry</Button>}</div>
        {calendar.isLoading ? <p className="p-5 text-[13px] text-muted">Loading entries…</p> : calendar.isError ? <p className="p-5 text-[13px] text-muted">Entries are unavailable. Retry loading the calendar.</p> : selectedEntries.length === 0 ? <p className="p-5 text-[13px] text-muted">No events or holidays on this date.</p> : (
          <ul className="divide-y divide-line">{selectedEntries.map((entry) => <li key={entry.id} className="px-5 py-4">
            <div className="flex gap-2 items-start"><p className="text-[13px] font-medium break-words min-w-0">{entry.title}</p><Badge variant={entry.holiday ? 'warning' : 'info'}>{entry.holiday ? 'Holiday' : 'Event'}</Badge></div>
            <p className="text-[12px] text-muted mt-1">{entry.government ? `Government holiday · ${entry.source}` : `Added by ${entry.createdByName}`}{entry.holiday ? ' · No classes' : ''}</p>
          </li>)}</ul>
        )}
      </section>
      {canCreate && formDate && <CalendarEntryForm date={formDate} onClose={() => setFormDate(null)} />}
    </PageContainer>
  );
}

export default function CalendarPage() {
  return <RequireRole roles={['ROLE_STAFF', 'ROLE_STUDENT', 'ROLE_HOD']}><DepartmentCalendar /></RequireRole>;
}
