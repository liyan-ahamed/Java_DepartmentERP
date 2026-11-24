export interface CalendarEntry {
  id: string;
  title: string;
  startDate: string;
  endDate: string;
  holiday: boolean;
  government: boolean;
  source: string;
  createdByName: string | null;
}

export interface CalendarResponse {
  entries: CalendarEntry[];
  governmentYearsAvailable: number[];
}

export interface CreateCalendarEntry {
  date: string;
  title: string;
  holiday: boolean;
}
