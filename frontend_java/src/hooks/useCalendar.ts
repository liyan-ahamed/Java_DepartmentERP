import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { calendarService } from '@/services/api/calendar.service';

export const useCalendar = (from: string, to: string) => useQuery({
  queryKey: ['calendar', from, to],
  queryFn: () => calendarService.list(from, to),
  refetchInterval: 60_000,
});

export const useCreateCalendarEntry = () => {
  const client = useQueryClient();
  return useMutation({
    mutationFn: calendarService.create,
    onSuccess: () => Promise.all([
      client.invalidateQueries({ queryKey: ['calendar'] }),
      client.invalidateQueries({ queryKey: ['notifications'] }),
      client.invalidateQueries({ queryKey: ['notificationsUnreadCount'] }),
    ]),
  });
};
