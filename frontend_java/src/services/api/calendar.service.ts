import { apiClient } from '@/lib/axios';
import { CalendarEntry, CalendarResponse, CreateCalendarEntry } from '@/types/calendar';

export const calendarService = {
  list: async (dateFrom: string, dateTo: string): Promise<CalendarResponse> => {
    const response = await apiClient.get('/calendar', { params: { dateFrom, dateTo } });
    return response.data.data;
  },
  create: async (payload: CreateCalendarEntry): Promise<CalendarEntry> => {
    const response = await apiClient.post('/calendar', payload);
    return response.data.data;
  },
};
