import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import TransactionsPage from './TransactionsPage'

vi.mock('../lib/finance', () => ({
  getTransactions: vi.fn().mockResolvedValue({
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 20,
  }),
}))

vi.mock('../lib/categories', () => ({
  getCategories: vi.fn().mockResolvedValue([
    { id: 'cat-1', name: 'Alimentação', color: '#ff0000', isSystem: false },
  ]),
}))

function createWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={qc}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  )
}

describe('TransactionsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the heading', () => {
    render(<TransactionsPage />, { wrapper: createWrapper() })
    expect(screen.getByRole('heading', { name: /transações/i })).toBeInTheDocument()
  })

  it('renders month picker with navigation buttons', () => {
    render(<TransactionsPage />, { wrapper: createWrapper() })
    expect(screen.getByText(/mês/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /mês anterior/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /próximo mês/i })).toBeInTheDocument()
  })

  it('renders bank filter select', () => {
    render(<TransactionsPage />, { wrapper: createWrapper() })
    expect(screen.getByLabelText(/banco/i)).toBeInTheDocument()
  })

  it('renders category filter select', () => {
    render(<TransactionsPage />, { wrapper: createWrapper() })
    expect(screen.getByLabelText(/categoria/i)).toBeInTheDocument()
  })

  it('renders type filter select', () => {
    render(<TransactionsPage />, { wrapper: createWrapper() })
    expect(screen.getByLabelText(/tipo/i)).toBeInTheDocument()
  })

  it('shows loading state initially', () => {
    render(<TransactionsPage />, { wrapper: createWrapper() })
    expect(screen.getByText(/carregando/i)).toBeInTheDocument()
  })
})
