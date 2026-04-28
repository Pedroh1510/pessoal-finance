import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { describe, it, expect, vi } from 'vitest'
import PrivateRoute from './PrivateRoute'
import * as AuthContext from '../contexts/AuthContext'

vi.mock('../contexts/AuthContext')

describe('PrivateRoute', () => {
  it('shows loading when isLoading is true', () => {
    vi.mocked(AuthContext.useAuth).mockReturnValue({
      user: null, isLoading: true, login: vi.fn(), logout: vi.fn(),
    })
    render(
      <MemoryRouter>
        <Routes>
          <Route path="/" element={<PrivateRoute><div>protected</div></PrivateRoute>} />
        </Routes>
      </MemoryRouter>
    )
    expect(screen.getByText('Carregando...')).toBeInTheDocument()
  })

  it('redirects to /login when user is null', async () => {
    vi.mocked(AuthContext.useAuth).mockReturnValue({
      user: null, isLoading: false, login: vi.fn(), logout: vi.fn(),
    })
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<PrivateRoute><div>protected</div></PrivateRoute>} />
          <Route path="/login" element={<div>login page</div>} />
        </Routes>
      </MemoryRouter>
    )
    await waitFor(() => expect(screen.getByText('login page')).toBeInTheDocument())
    expect(screen.queryByText('protected')).not.toBeInTheDocument()
  })

  it('renders children when user is authenticated', () => {
    vi.mocked(AuthContext.useAuth).mockReturnValue({
      user: { id: '1', email: 'u@test.com', name: 'U' },
      isLoading: false, login: vi.fn(), logout: vi.fn(),
    })
    render(
      <MemoryRouter>
        <Routes>
          <Route path="/" element={<PrivateRoute><div>protected content</div></PrivateRoute>} />
        </Routes>
      </MemoryRouter>
    )
    expect(screen.getByText('protected content')).toBeInTheDocument()
  })
})
