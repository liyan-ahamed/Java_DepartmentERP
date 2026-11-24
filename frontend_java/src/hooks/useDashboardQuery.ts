import { useQuery } from '@tanstack/react-query';
import { dashboardService } from '@/services/api/dashboard.service';

export const useDashboardSummary = () => useQuery({
  queryKey: ['dashboardSummary'],
  queryFn: dashboardService.getSummary,
});
