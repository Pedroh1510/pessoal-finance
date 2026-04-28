import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import api from '../lib/api'

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  if (!token) {
    return (
      <div className="auth-page">
        <p>
          Link inválido.{' '}
          <Link to="/forgot-password">Solicitar novo link</Link>
        </p>
      </div>
    )
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await api.post('/auth/reset-password', { token, newPassword: password })
      navigate('/login')
    } catch {
      setError('Token inválido ou expirado')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-page">
      <h1>Nova senha</h1>
      {error && <p role="alert" className="auth-error">{error}</p>}
      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="password">Nova senha</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
          />
        </div>
        <button type="submit" disabled={loading}>
          {loading ? 'Salvando...' : 'Redefinir senha'}
        </button>
      </form>
    </div>
  )
}
