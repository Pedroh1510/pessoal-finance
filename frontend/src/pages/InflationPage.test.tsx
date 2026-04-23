import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { type ReactNode } from 'react'
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
  return ({ children }: { children: ReactNode }) => (
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

  it('shows selected file count when multiple files are chosen', () => {
    render(<InflationPage />, { wrapper: createWrapper() })
    const fileA = new File(['a'], 'jan.xls', { type: 'application/vnd.ms-excel' })
    const fileB = new File(['b'], 'fev.xls', { type: 'application/vnd.ms-excel' })
    fireEvent.change(screen.getByLabelText(/arquivo/i), { target: { files: [fileA, fileB] } })
    expect(screen.getByText(/2 arquivos selecionados/i)).toBeInTheDocument()
  })

  it('calls uploadInflation once per file when multiple files are selected', async () => {
    render(<InflationPage />, { wrapper: createWrapper() })
    const fileA = new File(['a'], 'jan.xls', { type: 'application/vnd.ms-excel' })
    const fileB = new File(['b'], 'fev.xls', { type: 'application/vnd.ms-excel' })
    fireEvent.change(screen.getByLabelText(/arquivo/i), { target: { files: [fileA, fileB] } })
    fireEvent.click(screen.getByRole('button', { name: /importar/i }))
    await waitFor(() => expect(inflation.uploadInflation).toHaveBeenCalledTimes(2))
    expect(inflation.uploadInflation).toHaveBeenCalledWith(fileA)
    expect(inflation.uploadInflation).toHaveBeenCalledWith(fileB)
  })

  it('shows aggregated result after uploading multiple files', async () => {
    vi.mocked(inflation.uploadInflation)
      .mockResolvedValueOnce({ purchasesCreated: 2, purchasesSkipped: 0, itemsImported: 5 })
      .mockResolvedValueOnce({ purchasesCreated: 1, purchasesSkipped: 1, itemsImported: 3 })
    render(<InflationPage />, { wrapper: createWrapper() })
    const fileA = new File(['a'], 'jan.xls', { type: 'application/vnd.ms-excel' })
    const fileB = new File(['b'], 'fev.xls', { type: 'application/vnd.ms-excel' })
    fireEvent.change(screen.getByLabelText(/arquivo/i), { target: { files: [fileA, fileB] } })
    fireEvent.click(screen.getByRole('button', { name: /importar/i }))
    await waitFor(() =>
      expect(screen.getByText(/3 notas.*1 já existia.*8 itens/i)).toBeInTheDocument()
    )
  })

  it('renders description filter input', () => {
    render(<InflationPage />, { wrapper: createWrapper() })
    expect(screen.getByLabelText(/descrição/i)).toBeInTheDocument()
  })

  it('passes description to getInflationItems when typed', async () => {
    render(<InflationPage />, { wrapper: createWrapper() })
    fireEvent.change(screen.getByLabelText(/descrição/i), { target: { value: 'MILHO' } })
    await waitFor(() =>
      expect(inflation.getInflationItems).toHaveBeenCalledWith(
        expect.objectContaining({ description: 'MILHO' })
      )
    )
  })

  it('calls getInflationComparison when description + from + to are filled', async () => {
    render(<InflationPage />, { wrapper: createWrapper() })
    fireEvent.change(screen.getByLabelText(/descrição/i), { target: { value: 'MILHO' } })
    fireEvent.change(screen.getByLabelText(/^de$/i), { target: { value: '2024-01' } })
    fireEvent.change(screen.getByLabelText(/^até$/i), { target: { value: '2025-01' } })
    await waitFor(() =>
      expect(inflation.getInflationComparison).toHaveBeenCalledWith(
        expect.objectContaining({ description: 'MILHO', from: '2024-01', to: '2025-01' })
      )
    )
  })

  it('shows upload progress while importing multiple files', async () => {
    let resolveFirst!: (v: unknown) => void
    vi.mocked(inflation.uploadInflation)
      .mockImplementationOnce(
        () => new Promise((res) => { resolveFirst = res }),
      )
      .mockResolvedValue({ purchasesCreated: 1, purchasesSkipped: 0, itemsImported: 2 })

    render(<InflationPage />, { wrapper: createWrapper() })
    const fileA = new File(['a'], 'jan.xls', { type: 'application/vnd.ms-excel' })
    const fileB = new File(['b'], 'fev.xls', { type: 'application/vnd.ms-excel' })
    fireEvent.change(screen.getByLabelText(/arquivo/i), { target: { files: [fileA, fileB] } })
    fireEvent.click(screen.getByRole('button', { name: /importar/i }))

    expect(await screen.findByText(/importando 1 de 2/i)).toBeInTheDocument()

    resolveFirst({ purchasesCreated: 1, purchasesSkipped: 0, itemsImported: 2 })
    await waitFor(() =>
      expect(screen.queryByText(/importando 1 de 2/i)).not.toBeInTheDocument(),
    )
  })
})
