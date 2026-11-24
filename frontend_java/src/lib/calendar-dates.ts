import { CalendarEntry } from '@/types/calendar';
import { StudentSchedule } from '@/types/schedule';

const pad = (n: number) => String(n).padStart(2, '0');
export const localDate = (date = new Date()) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
export const dateValue = (date: string) => new Date(`${date}T00:00:00`);
export const dateLabel = (date: string) => dateValue(date).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
export const shiftMonth = (month: string, by: number) => {
  const [year, number] = month.split('-').map(Number);
  return localDate(new Date(year, number - 1 + by, 1)).slice(0, 7);
};
export const addDays = (date: string, count: number) => {
  const value = dateValue(date);
  value.setDate(value.getDate() + count);
  return localDate(value);
};

/** Monday-first complete weeks, computed using local dates (never UTC conversion). */
export const monthDays = (month: string): string[] => {
  const [year, number] = month.split('-').map(Number);
  const first = new Date(year, number - 1, 1);
  const last = new Date(year, number, 0);
  const leading = (first.getDay() + 6) % 7;
  const count = Math.ceil((leading + last.getDate()) / 7) * 7;
  return Array.from({ length: count }, (_, index) => localDate(new Date(year, number - 1, 1 - leading + index)));
};

export const entriesOn = (entries: CalendarEntry[], date: string) =>
  entries.filter((entry) => entry.startDate <= date && entry.endDate >= date);

export const upcomingEntries = (entries: CalendarEntry[], today: string) => entries
  .filter((entry) => entry.endDate >= today)
  .toSorted((a, b) => (a.startDate < today ? today : a.startDate).localeCompare(b.startDate < today ? today : b.startDate) || a.title.localeCompare(b.title));

/** Keep the existing schedule audience restrictions; only filter expired deadlines locally. */
export const upcomingDeadlines = (rows: StudentSchedule[], now = new Date()) => {
  const today = localDate(now);
  const time = `${pad(now.getHours())}:${pad(now.getMinutes())}`;
  return rows.filter((row) => row.due_date && (row.due_date > today ||
    (row.due_date === today && (!row.due_time || row.due_time >= time))))
    .toSorted((a, b) => (a.due_date ?? '').localeCompare(b.due_date ?? '') ||
      (a.due_time ?? '23:59:59').localeCompare(b.due_time ?? '23:59:59') || a.id - b.id);
};
