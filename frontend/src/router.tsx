import { createBrowserRouter } from 'react-router-dom'
import Layout from './components/Layout'
import DashboardPage from './pages/DashboardPage'
import TransactionsPage from './pages/TransactionsPage'
import TransactionDetailPage from './pages/TransactionDetailPage'
import InflationPage from './pages/InflationPage'
import PatrimonyPage from './pages/PatrimonyPage'
import SettingsPage from './pages/SettingsPage'
import UploadsPage from './pages/UploadsPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true,                  element: <DashboardPage /> },
      { path: 'transactions',         element: <TransactionsPage /> },
      { path: 'transactions/:id',     element: <TransactionDetailPage /> },
      { path: 'inflation',            element: <InflationPage /> },
      { path: 'patrimony',            element: <PatrimonyPage /> },
      { path: 'settings',             element: <SettingsPage /> },
      { path: 'uploads',              element: <UploadsPage /> },
    ],
  },
])
