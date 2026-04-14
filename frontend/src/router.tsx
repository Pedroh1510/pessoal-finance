import { createBrowserRouter } from 'react-router-dom'
import Layout from './components/Layout'
import DashboardPage from './pages/DashboardPage'
import TransactionsPage from './pages/TransactionsPage'
import InflationPage from './pages/InflationPage'
import PatrimonyPage from './pages/PatrimonyPage'
import SettingsPage from './pages/SettingsPage'
import UploadsPage from './pages/UploadsPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true,          element: <DashboardPage /> },
      { path: 'transactions', element: <TransactionsPage /> },
      { path: 'inflation',    element: <InflationPage /> },
      { path: 'patrimony',    element: <PatrimonyPage /> },
      { path: 'settings',     element: <SettingsPage /> },
      { path: 'uploads',      element: <UploadsPage /> },
    ],
  },
])
