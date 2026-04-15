import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import TransactionDetailPage from './TransactionDetailPage'

vi.mock('../lib/finance', () => ({
  getTransaction: vi.fn().mockResolvedValue({
    transactionId: 'tx-123',
    date: '2024-01-15',
    amount: 250.5,
    recipient: 'Supermercado XYZ',
    description: 'Compra mensal',
    categoryId: 'cat-1',
    categoryName: 'Alimentação',
    type: 'EXPENSE',
    bankName: 'NUBANK',
  }),
  categorizeTransaction: vi.fn().mockResolvedValue(undefined),
}))

vi.mock('../lib/categories', () => ({
  getCategories: vi.fn().mockResolvedValue([
    { id: 'cat-1', name: 'Alimentação', color: '#ff0000', isSystem: false },
    { id: 'cat-2', name: 'Transporte', color: '#0000ff', isSystem: false },
  ]),
}))

function createWrapper(id = 'tx-123') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[`/transactions/${id}`]}>
        <Routes>
          <Route path="/transactions/:id" element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('TransactionDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows loading state initially', () => {
    render(<TransactionDetailPage />, { wrapper: createWrapper() })
    expect(screen.getByText(/carregando/i)).toBeInTheDocument()
  })

  it('renders the page heading after data loads', async () => {
    render(<TransactionDetailPage />, { wrapper: createWrapper() })
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: /detalhes da transação/i })).toBeInTheDocument(),
    )
  })

  it('renders back button after data loads', async () => {
    render(<TransactionDetailPage />, { wrapper: createWrapper() })
    await waitFor(() => expect(screen.getByText(/← voltar/i)).toBeInTheDocument())
  })

  it('renders category select after data loads', async () => {
    render(<TransactionDetailPage />, { wrapper: createWrapper() })
    await waitFor(() =>
      expect(screen.getByLabelText(/selecionar categoria/i)).toBeInTheDocument(),
    )
  })

  it('renders save button after data loads', async () => {
    render(<TransactionDetailPage />, { wrapper: createWrapper() })
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /salvar categoria/i })).toBeInTheDocument(),
    )
  })

  it('shows transaction details after data loads', async () => {
    render(<TransactionDetailPage />, { wrapper: createWrapper() })
    await waitFor(() => expect(screen.getByText('Supermercado XYZ')).toBeInTheDocument())
  })
})
