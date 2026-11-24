import { useInfiniteQuery, useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { scheduleService } from '@/services/api/schedule.service';
import { CreateStudentSchedulePayload, StudentScheduleType } from '@/types/schedule';

export const useScheduleAudience = (enabled = true) => useQuery({
  queryKey: ['studentScheduleAudience'], queryFn: scheduleService.getAudience, enabled,
});

export const useStudentSchedules = (type: StudentScheduleType, enabled = true) => useQuery({
  queryKey: ['studentSchedules', type], queryFn: () => scheduleService.getStudentSchedules(type), enabled,
});

export const useCreateStudentSchedule = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateStudentSchedulePayload) => scheduleService.createStudentSchedule(payload),
    onSuccess: (_data, payload) => Promise.all([
      queryClient.invalidateQueries({ queryKey: ['studentSchedules', payload.type] }),
      queryClient.resetQueries({ queryKey: ['staffScheduleHistory', payload.type] }),
    ]),
  });
};

export const useStaffScheduleHistory = (type: StudentScheduleType) => useInfiniteQuery({
  queryKey: ['staffScheduleHistory', type],
  queryFn: ({ pageParam }) => scheduleService.getHistory(type, pageParam),
  initialPageParam: 0,
  getNextPageParam: (page) => page.last ? undefined : page.page + 1,
  refetchInterval: false,
  gcTime: 0,
  staleTime: 0,
});

export const useStaffPollResponses = (id: number) => useInfiniteQuery({
  queryKey: ['staffPollResponses', id],
  queryFn: ({ pageParam }) => scheduleService.getPollResponses(id, pageParam),
  initialPageParam: 0,
  getNextPageParam: (page) => page.last ? undefined : page.page + 1,
  refetchInterval: false,
  gcTime: 0,
  staleTime: 0,
});

export const useSubmitPollResponse = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ pollId, optionIndex }: { pollId: number; optionIndex: number }) =>
      scheduleService.submitPollResponse(pollId, optionIndex),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['studentSchedules', 'POLL'] }),
  });
};
