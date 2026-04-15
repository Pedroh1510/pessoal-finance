import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import DashboardPage from './DashboardPage'

vi.mock('../lib/finance', () => ({
  getTransactions: vi.fn().mockResolvedValue({
    content: [
      {
        transactionId: '1',
        date: '2024-01-15',
        amount: 5000,
        recipient: 'Salário',
        description: null,
        categoryId: null,
        categoryName: null,
        type: 'INCOME',
        bankName: 'NUBANK',
      },
      {
        transactionId: '2',
        date: '2024-01-20',
        amount: 200,
        recipient: 'Mercado',
        description: null,
        categoryId: 'cat-1',
        categoryName: 'Alimentação',
        type: 'EXPENSE',
        bankName: 'NUBANK',
      },
    ],
    totalElements: 2,
    totalPages: 1,
    number: 0,
    size: 1000,
  }),
}))

function createWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows loading state initially', () => {
    render(<DashboardPage />, { wrapper: createWrapper() })
    expect(screen.getByText(/carregando dashboard/i)).toBeInTheDocument()
  })

  it('renders the dashboard heading after data loads', async () => {
    render(<DashboardPage />, { wrapper: createWrapper() })
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: /dashboard/i })).toBeInTheDocument(),
    )
  })

  it('renders saldo cards after data loads', async () => {
    render(<DashboardPage />, { wrapper: createWrapper() })
    await waitFor(() => {
      expect(screen.getAllByText(/receita/i).length).toBeGreaterThan(0)
      expect(screen.getAllByText(/despesa/i).length).toBeGreaterThan(0)
      expect(screen.getByText(/saldo do mês/i)).toBeInTheDocument()
    })
  })

  it('renders patrimônio placeholder', async () => {
    render(<DashboardPage />, { wrapper: createWrapper() })
    await waitFor(() => expect(screen.getByText(/patrimônio/i)).toBeInTheDocument())
  })
})
