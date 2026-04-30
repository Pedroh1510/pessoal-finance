import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import SettingsPage from './SettingsPage'
import * as financeLib from '../lib/finance'

vi.mock('../lib/categories', () => ({
  getCategories: vi.fn().mockResolvedValue([
    { id: 'cat-1', name: 'Alimentação', color: '#ff0000', isSystem: false },
    { id: 'cat-sys', name: 'Sistema', color: '#000000', isSystem: true },
  ]),
  createCategory: vi
    .fn()
    .mockResolvedValue({ id: 'new', name: 'Nova', color: '#aabbcc', isSystem: false }),
  updateCategory: vi
    .fn()
    .mockResolvedValue({ id: 'cat-1', name: 'Alimentação', color: '#ff0000', isSystem: false }),
  deleteCategory: vi.fn().mockResolvedValue(undefined),
}))

vi.mock('../lib/finance', () => ({
  getRecipientRules: vi.fn().mockResolvedValue([]),
  createRecipientRule: vi.fn().mockResolvedValue(undefined),
  deleteRecipientRule: vi.fn().mockResolvedValue(undefined),
  getInternalAccountRules: vi.fn().mockResolvedValue([]),
  createInternalAccountRule: vi.fn().mockResolvedValue(undefined),
  deleteInternalAccountRule: vi.fn().mockResolvedValue(undefined),
  reprocessTransactions: vi.fn().mockResolvedValue({ jobId: 'job-1' }),
}))

vi.mock('../lib/jobs', () => ({
  getJob: vi.fn().mockResolvedValue({
    id: 'job-1', type: 'REPROCESS', status: 'COMPLETED',
    result: { categorized: 2, typeChanged: 1 }, errorMessage: null, createdAt: '', updatedAt: '',
  }),
  isTerminal: (status: string) => status === 'COMPLETED' || status === 'FAILED',
}))

function createWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
}

describe('SettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the heading', () => {
    render(<SettingsPage />, { wrapper: createWrapper() })
    expect(screen.getByRole('heading', { name: /configurações/i })).toBeInTheDocument()
  })

  it('renders all four tabs', () => {
    render(<SettingsPage />, { wrapper: createWrapper() })
    expect(screen.getByRole('tab', { name: /categorias/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /regras de destinatário/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /contas internas/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /reprocessar/i })).toBeInTheDocument()
  })

  it('shows categories tab by default after data loads', async () => {
    render(<SettingsPage />, { wrapper: createWrapper() })
    await waitFor(() =>
      expect(screen.getByLabelText(/nome da categoria/i)).toBeInTheDocument(),
    )
  })

  it('switches to recipient rules tab', async () => {
    render(<SettingsPage />, { wrapper: createWrapper() })
    await userEvent.click(screen.getByRole('tab', { name: /regras de destinatário/i }))
    await waitFor(() =>
      expect(screen.getByLabelText(/padrão do destinatário/i)).toBeInTheDocument(),
    )
  })

  it('switches to internal accounts tab', async () => {
    render(<SettingsPage />, { wrapper: createWrapper() })
    await userEvent.click(screen.getByRole('tab', { name: /contas internas/i }))
    await waitFor(() =>
      expect(screen.getByLabelText(/^identificador/i)).toBeInTheDocument(),
    )
  })

  it('switches to reprocess tab and triggers reprocessing', async () => {
    render(<SettingsPage />, { wrapper: createWrapper() })

    await userEvent.click(screen.getByRole('tab', { name: /reprocessar/i }))

    const btn = await screen.findByRole('button', { name: /reprocessar transações/i })
    expect(btn).toBeInTheDocument()

    await userEvent.click(btn)

    await waitFor(() =>
      expect(vi.mocked(financeLib.reprocessTransactions)).toHaveBeenCalledTimes(1)
    )

    await waitFor(() =>
      expect(screen.getByText(/aguardando processamento/i)).toBeInTheDocument()
    )
  })
})
