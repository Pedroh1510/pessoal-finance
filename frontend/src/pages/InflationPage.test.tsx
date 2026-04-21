import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import InflationPage from './InflationPage'
import * as inflation from '../lib/inflation'

vi.mock('../lib/inflation', () => ({
  uploadInflation: vi.fn().mockResolvedValue({
    purchasesCreated: 1,
    purchasesSkipped: 0,
    itemsImported: 3,
  }),
  getInflationItems: vi.fn().mockResolvedValue([]),
  getInflationComparison: vi.fn().mockResolvedValue({
    ncm: '', description: '', prices: [],
  }),
}))

function createWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
}

describe('InflationPage', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders heading', () => {
    render(<InflationPage />, { wrapper: createWrapper() })
    expect(screen.getByRole('heading', { name: /infla/i })).toBeInTheDocument()
  })

  it('renders file input', () => {
    render(<InflationPage />, { wrapper: createWrapper() })
    expect(screen.getByLabelText(/arquivo/i)).toBeInTheDocument()
  })

  it('upload button disabled when no file selected', () => {
    render(<InflationPage />, { wrapper: createWrapper() })
    expect(screen.getByRole('button', { name: /importar/i })).toBeDisabled()
  })

  it('calls uploadInflation with file on submit', async () => {
    render(<InflationPage />, { wrapper: createWrapper() })
    const file = new File(['data'], '2603_produtos.xls', { type: 'application/vnd.ms-excel' })
    fireEvent.change(screen.getByLabelText(/arquivo/i), { target: { files: [file] } })
    fireEvent.click(screen.getByRole('button', { name: /importar/i }))
    await waitFor(() =>
      expect(inflation.uploadInflation).toHaveBeenCalledWith(file)
    )
  })

  it('shows success message with purchases and items count after upload', async () => {
    render(<InflationPage />, { wrapper: createWrapper() })
    const file = new File(['data'], '2603_produtos.xls', { type: 'application/vnd.ms-excel' })
    fireEvent.change(screen.getByLabelText(/arquivo/i), { target: { files: [file] } })
    fireEvent.click(screen.getByRole('button', { name: /importar/i }))
    await waitFor(() =>
      expect(screen.getByText(/1 nota.*3 itens/i)).toBeInTheDocument()
    )
  })

  it('renders NCM filter input', () => {
    render(<InflationPage />, { wrapper: createWrapper() })
    expect(screen.getByLabelText(/^ncm$/i)).toBeInTheDocument()
  })

  it('shows items table when items are returned', async () => {
    vi.mocked(inflation.getInflationItems).mockResolvedValue([
      {
        id: '1', purchaseId: 'p1', period: '2026-03', emitente: 'MERCADO X',
        chave: '35260348564323000134650290000032581020295871',
        productCode: '12403', ncm: '10059010', description: 'MILHO PIPOCA',
        quantity: 1.5, unitPrice: 8.90, totalPrice: 13.35,
      },
    ])
    render(<InflationPage />, { wrapper: createWrapper() })
    await waitFor(() => expect(screen.getByText('MILHO PIPOCA')).toBeInTheDocument())
    expect(screen.getByText('10059010')).toBeInTheDocument()
  })

  it('does not show store or date fields in the upload form', () => {
    render(<InflationPage />, { wrapper: createWrapper() })
    expect(screen.queryByLabelText(/loja/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/data da compra/i)).not.toBeInTheDocument()
  })
})
