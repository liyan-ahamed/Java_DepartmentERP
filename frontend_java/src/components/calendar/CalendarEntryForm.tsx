'use client';

import { FormEvent, useState } from 'react';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { useCreateCalendarEntry } from '@/hooks/useCalendar';
import { useToast } from '@/providers/toast-provider';
import { dateLabel } from '@/lib/calendar-dates';
import { getApiErrorMessage } from '@/lib/api-error';

export function CalendarEntryForm({ date, onClose }: { date: string; onClose: () => void }) {
  const [title, setTitle] = useState('');
  const [holiday, setHoliday] = useState(false);
  const create = useCreateCalendarEntry();
  const toast = useToast();
  const close = () => { if (!create.isPending) onClose(); };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!title.trim() || create.isPending) return;
    create.mutate({ date, title: title.trim(), holiday }, { onSuccess: () => {
      toast(holiday ? 'Holiday added. Students and HOD have been notified.' : 'Event added. Students and HOD have been notified.');
      onClose();
    } });
  };
  return (
    <Dialog title={`Add calendar entry · ${dateLabel(date)}`} onClose={close}>
      <form onSubmit={submit} className="space-y-5">
        <div>
          <label className="f-label" htmlFor="calendar-title">Event or holiday title</label>
          <Input id="calendar-title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={255}
            placeholder="e.g. Group Presentation Day" required disabled={create.isPending} />
        </div>
        <label className="flex items-start gap-3 text-[13px] cursor-pointer">
          <input type="checkbox" checked={holiday} onChange={(e) => setHoliday(e.target.checked)} disabled={create.isPending} className="mt-1 accent-accent" />
          <span><strong className="block font-medium">Mark this date as a holiday</strong><span className="text-muted">No classes on this date.</span></span>
        </label>
        <p className="text-[12px] text-muted">This entry is visible to the department. All student accounts and the HOD will receive a notification.</p>
        {create.isError && <p role="alert" className="text-[13px] text-danger">{getApiErrorMessage(create.error, 'Unable to save the calendar entry. Please try again.')}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={close} disabled={create.isPending}>Cancel</Button>
          <Button type="submit" isLoading={create.isPending} disabled={!title.trim()}>{holiday ? 'Add holiday' : 'Add event'}</Button>
        </div>
      </form>
    </Dialog>
  );
}
