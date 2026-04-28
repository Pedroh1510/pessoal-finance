import { Navigate } from 'react-router-dom'
import { ReactNode } from 'react'
import { useAuth } from '../contexts/AuthContext'

interface Props {
  children: ReactNode
}

export default function PrivateRoute({ children }: Props) {
  const { user, isLoading } = useAuth()

  if (isLoading) return <div className="auth-loading">Carregando...</div>
  if (!user) return <Navigate to="/login" replace />
  return <>{children}</>
}
