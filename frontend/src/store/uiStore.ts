import { create } from 'zustand'
import dayjs, { Dayjs } from 'dayjs'

interface UIStore {
  // 搜索页
  currentQaId: string | null
  highlightedCiteNo: number | null
  setCurrentQaId: (id: string | null) => void
  setHighlightedCiteNo: (n: number | null) => void

  // 报表页时间窗口
  reportDateRange: [Dayjs, Dayjs]
  setReportDateRange: (range: [Dayjs, Dayjs]) => void
}

export const useUIStore = create<UIStore>((set) => ({
  currentQaId: null,
  highlightedCiteNo: null,
  setCurrentQaId: (id) => set({ currentQaId: id }),
  setHighlightedCiteNo: (n) => set({ highlightedCiteNo: n }),

  reportDateRange: [dayjs().subtract(7, 'day'), dayjs()],
  setReportDateRange: (range) => set({ reportDateRange: range }),
}))
