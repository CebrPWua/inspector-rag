import { createBrowserRouter, Navigate } from 'react-router-dom'
import App from './App'
import QaSearchPage from './pages/QaSearch'
import FileManagementPage from './pages/FileManagement'
import FileDetailPage from './pages/FileManagement/FileDetail'
import TaskOpsPage from './pages/TaskOps'
import RecordsPage from './pages/Records'
import QaDetailPage from './pages/Records/QaDetail'
import QaReplayPage from './pages/Records/QaReplay'
import QualityReportPage from './pages/QualityReport'
import QaConfigPage from './pages/QaConfig'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    children: [
      { index: true, element: <QaSearchPage /> },
      { path: 'files', element: <FileManagementPage /> },
      { path: 'files/:docId', element: <FileDetailPage /> },
      { path: 'tasks', element: <Navigate to="/tasks/dead-letter" replace /> },
      { path: 'tasks/dead-letter', element: <TaskOpsPage /> },
      { path: 'records', element: <RecordsPage /> },
      { path: 'records/:qaId', element: <QaDetailPage /> },
      { path: 'records/:qaId/replay', element: <QaReplayPage /> },
      { path: 'reports', element: <QualityReportPage /> },
      { path: 'qa-config', element: <QaConfigPage /> },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
])
