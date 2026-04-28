import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AuthProvider, useAuth } from './AuthContext'
import api from '../lib/api'

vi.mock('../lib/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

function UserDisplay() {
  const { user, isLoading } = useAuth()
  if (isLoading) return <div>loading</div>
  return <div>{user ? user.email : 'no user'}</div>
}

describe('AuthContext', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows loading initially, then user when /me succeeds', async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: { id: '1', email: 'user@test.com', name: 'User' },
    })
    render(<AuthProvider><UserDisplay /></AuthProvider>)

    expect(screen.getByText('loading')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('user@test.com')).toBeInTheDocument())
  })

  it('shows no user when /me returns 401', async () => {
    vi.mocked(api.get).mockRejectedValue({ response: { status: 401 } })
    render(<AuthProvider><UserDisplay /></AuthProvider>)

    await waitFor(() => expect(screen.getByText('no user')).toBeInTheDocument())
  })

  it('login() calls POST /auth/login and updates user', async () => {
    vi.mocked(api.get).mockRejectedValue({})
    vi.mocked(api.post).mockResolvedValue({
      data: { id: '1', email: 'user@test.com', name: 'User' },
    })

    function LoginButton() {
      const { user, login } = useAuth()
      return (
        <>
          <button onClick={() => login('user@test.com', 'pass')}>login</button>
          <div>{user?.email ?? 'no user'}</div>
        </>
      )
    }

    render(<AuthProvider><LoginButton /></AuthProvider>)
    await waitFor(() => expect(screen.queryByText('loading')).not.toBeInTheDocument())

    await userEvent.click(screen.getByText('login'))

    await waitFor(() => expect(screen.getByText('user@test.com')).toBeInTheDocument())
    expect(api.post).toHaveBeenCalledWith('/auth/login', { email: 'user@test.com', password: 'pass' })
  })

  it('logout() calls POST /auth/logout and clears user', async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: { id: '1', email: 'user@test.com', name: 'User' },
    })
    vi.mocked(api.post).mockResolvedValue({})

    function LogoutButton() {
      const { user, logout } = useAuth()
      return (
        <>
          <button onClick={logout}>logout</button>
          <div>{user?.email ?? 'no user'}</div>
        </>
      )
    }

    render(<AuthProvider><LogoutButton /></AuthProvider>)
    await waitFor(() => expect(screen.getByText('user@test.com')).toBeInTheDocument())

    await userEvent.click(screen.getByText('logout'))

    await waitFor(() => expect(screen.getByText('no user')).toBeInTheDocument())
    expect(api.post).toHaveBeenCalledWith('/auth/logout')
  })
})
