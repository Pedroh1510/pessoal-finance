import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import RegisterPage from './RegisterPage'
import * as AuthContext from '../contexts/AuthContext'
import api from '../lib/api'

vi.mock('../contexts/AuthContext')
vi.mock('../lib/api', () => ({ default: { post: vi.fn() } }))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => mockNavigate }
})

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(AuthContext.useAuth).mockReturnValue({
      user: null, isLoading: false, login: vi.fn(), logout: vi.fn(),
    })
  })

  it('calls register API then login, then navigates to /', async () => {
    const mockLogin = vi.fn().mockResolvedValue(undefined)
    vi.mocked(AuthContext.useAuth).mockReturnValue({
      user: null, isLoading: false, login: mockLogin, logout: vi.fn(),
    })
    vi.mocked(api.post).mockResolvedValue({})

    render(<MemoryRouter><RegisterPage /></MemoryRouter>)

    await userEvent.type(screen.getByLabelText(/nome/i), 'Pedro Silva')
    await userEvent.type(screen.getByLabelText(/email/i), 'pedro@test.com')
    await userEvent.type(screen.getByLabelText(/senha/i), 'password123')
    await userEvent.click(screen.getByRole('button', { name: /criar conta/i }))

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/auth/register', {
        name: 'Pedro Silva', email: 'pedro@test.com', password: 'password123',
      })
      expect(mockLogin).toHaveBeenCalledWith('pedro@test.com', 'password123')
      expect(mockNavigate).toHaveBeenCalledWith('/')
    })
  })

  it('shows error message when email is already registered', async () => {
    vi.mocked(api.post).mockRejectedValue({
      response: { data: { error: 'Email already registered' } },
    })
    render(<MemoryRouter><RegisterPage /></MemoryRouter>)

    await userEvent.type(screen.getByLabelText(/nome/i), 'Pedro')
    await userEvent.type(screen.getByLabelText(/email/i), 'existing@test.com')
    await userEvent.type(screen.getByLabelText(/senha/i), 'password123')
    await userEvent.click(screen.getByRole('button', { name: /criar conta/i }))

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('Email already registered')
    )
  })
})
