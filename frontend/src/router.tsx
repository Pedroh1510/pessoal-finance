import { createBrowserRouter } from 'react-router-dom'
import Layout from './components/Layout'
import PrivateRoute from './components/PrivateRoute'
import DashboardPage from './pages/DashboardPage'
import TransactionsPage from './pages/TransactionsPage'
import TransactionDetailPage from './pages/TransactionDetailPage'
import InflationPage from './pages/InflationPage'
import PatrimonyPage from './pages/PatrimonyPage'
import SettingsPage from './pages/SettingsPage'
import UploadsPage from './pages/UploadsPage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'

export const router = createBrowserRouter([
  { path: 'login',          element: <LoginPage /> },
  { path: 'register',       element: <RegisterPage /> },
  { path: 'forgot-password', element: <ForgotPasswordPage /> },
  { path: 'reset-password', element: <ResetPasswordPage /> },
  {
    path: '/',
    element: <PrivateRoute><Layout /></PrivateRoute>,
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
