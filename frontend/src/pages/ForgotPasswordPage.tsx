import { useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../lib/api'

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    try {
      await api.post('/auth/forgot-password', { email })
    } finally {
      setSent(true)
      setLoading(false)
    }
  }

  if (sent) {
    return (
      <div className="auth-page">
        <h1>Email enviado</h1>
        <p>Se o email estiver cadastrado, você receberá um link para redefinir sua senha.</p>
        <Link to="/login">Voltar ao login</Link>
      </div>
    )
  }

  return (
    <div className="auth-page">
      <h1>Esqueci minha senha</h1>
      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>
        <button type="submit" disabled={loading}>
          {loading ? 'Enviando...' : 'Enviar link'}
        </button>
      </form>
      <p><Link to="/login">Voltar ao login</Link></p>
    </div>
  )
}
