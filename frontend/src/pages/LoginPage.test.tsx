import { render, screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import LoginPage from './LoginPage'
import * as AuthContext from '../contexts/AuthContext'

vi.mock('../contexts/AuthContext')

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => mockNavigate }
})

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(AuthContext.useAuth).mockReturnValue({
      user: null, isLoading: false, login: vi.fn(), logout: vi.fn(),
    })
  })

  it('renders email and password fields', () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>)
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/senha/i)).toBeInTheDocument()
  })

  it('calls login with credentials and navigates to / on success', async () => {
    const mockLogin = vi.fn().mockResolvedValue(undefined)
    vi.mocked(AuthContext.useAuth).mockReturnValue({
      user: null, isLoading: false, login: mockLogin, logout: vi.fn(),
    })
    render(<MemoryRouter><LoginPage /></MemoryRouter>)

    await userEvent.type(screen.getByLabelText(/email/i), 'user@test.com')
    await userEvent.type(screen.getByLabelText(/senha/i), 'password123')
    await userEvent.click(screen.getByRole('button', { name: /entrar/i }))

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith('user@test.com', 'password123')
      expect(mockNavigate).toHaveBeenCalledWith('/')
    })
  })

  it('shows error message when login fails', async () => {
    const mockLogin = vi.fn().mockRejectedValue(new Error('401'))
    vi.mocked(AuthContext.useAuth).mockReturnValue({
      user: null, isLoading: false, login: mockLogin, logout: vi.fn(),
    })
    render(<MemoryRouter><LoginPage /></MemoryRouter>)

    await userEvent.type(screen.getByLabelText(/email/i), 'user@test.com')
    await userEvent.type(screen.getByLabelText(/senha/i), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: /entrar/i }))

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/inválidos/i)
    )
  })

  it('disables button while loading', async () => {
    let resolveLogin!: () => void
    const mockLogin = vi.fn().mockImplementation(
      () => new Promise<void>((resolve) => { resolveLogin = resolve })
    )
    vi.mocked(AuthContext.useAuth).mockReturnValue({
      user: null, isLoading: false, login: mockLogin, logout: vi.fn(),
    })
    render(<MemoryRouter><LoginPage /></MemoryRouter>)

    await userEvent.type(screen.getByLabelText(/email/i), 'u@test.com')
    await userEvent.type(screen.getByLabelText(/senha/i), 'pass1234')
    await userEvent.click(screen.getByRole('button', { name: /entrar/i }))

    expect(screen.getByRole('button')).toBeDisabled()

    await act(async () => { resolveLogin() })
    await waitFor(() => expect(screen.getByRole('button')).not.toBeDisabled())
  })
})
