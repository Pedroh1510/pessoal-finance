import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import UploadsPage from './UploadsPage'
import * as finance from '../lib/finance'

vi.mock('../lib/finance', () => ({
  uploadStatement: vi.fn().mockResolvedValue({ jobId: 'job-abc' }),
}))

vi.mock('../lib/jobs', () => ({
  getJob: vi.fn().mockResolvedValue({
    id: 'job-abc', type: 'FINANCE_UPLOAD', status: 'QUEUED',
    result: null, errorMessage: null, createdAt: '', updatedAt: '',
  }),
  isTerminal: (status: string) => status === 'COMPLETED' || status === 'FAILED',
}))

function createWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
}

function makeFile(name: string) {
  return new File(['%PDF'], name, { type: 'application/pdf' })
}

describe('UploadsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the heading', () => {
    render(<UploadsPage />, { wrapper: createWrapper() })
    expect(screen.getByRole('heading', { name: /upload de extrato/i })).toBeInTheDocument()
  })

  it('renders bank selector', () => {
    render(<UploadsPage />, { wrapper: createWrapper() })
    expect(screen.getByLabelText(/selecionar banco/i)).toBeInTheDocument()
  })

  it('renders all bank options', () => {
    render(<UploadsPage />, { wrapper: createWrapper() })
    expect(screen.getByText('Nubank')).toBeInTheDocument()
    expect(screen.getByText('Neon')).toBeInTheDocument()
    expect(screen.getByText('Inter')).toBeInTheDocument()
  })

  it('renders file input', () => {
    render(<UploadsPage />, { wrapper: createWrapper() })
    expect(screen.getByLabelText(/selecionar arquivos pdf/i)).toBeInTheDocument()
  })

  it('renders submit button', () => {
    render(<UploadsPage />, { wrapper: createWrapper() })
    expect(screen.getByRole('button', { name: /enviar extratos/i })).toBeInTheDocument()
  })

  it('submit button is disabled when no file selected', () => {
    render(<UploadsPage />, { wrapper: createWrapper() })
    expect(screen.getByRole('button', { name: /enviar extratos/i })).toBeDisabled()
  })

  it('allows selecting multiple files and shows count', async () => {
    render(<UploadsPage />, { wrapper: createWrapper() })
    const input = screen.getByLabelText(/selecionar arquivos pdf/i)
    const files = [makeFile('jan.pdf'), makeFile('feb.pdf')]
    fireEvent.change(input, { target: { files } })
    expect(screen.getByText('2 arquivo(s) selecionado(s)')).toBeInTheDocument()
  })

  it('uploads each file sequentially and shows results in history', async () => {
    vi.mocked(finance.uploadStatement).mockResolvedValue({ jobId: 'job-abc' })
    render(<UploadsPage />, { wrapper: createWrapper() })
    const input = screen.getByLabelText(/selecionar arquivos pdf/i)
    fireEvent.change(input, { target: { files: [makeFile('jan.pdf'), makeFile('feb.pdf')] } })
    fireEvent.click(screen.getByRole('button', { name: /enviar extratos/i }))
    await waitFor(() => expect(finance.uploadStatement).toHaveBeenCalledTimes(2))
    expect(screen.getByText('jan.pdf')).toBeInTheDocument()
    expect(screen.getByText('feb.pdf')).toBeInTheDocument()
  })

  it('shows error for failed files without stopping others', async () => {
    vi.mocked(finance.uploadStatement)
      .mockResolvedValueOnce({ jobId: 'job-abc' })
      .mockRejectedValueOnce(new Error('Arquivo inválido'))
    render(<UploadsPage />, { wrapper: createWrapper() })
    const input = screen.getByLabelText(/selecionar arquivos pdf/i)
    fireEvent.change(input, { target: { files: [makeFile('a.pdf'), makeFile('b.pdf')] } })
    fireEvent.click(screen.getByRole('button', { name: /enviar extratos/i }))
    await waitFor(() => expect(screen.getByText(/Arquivo inválido/)).toBeInTheDocument())
    expect(screen.getByText('a.pdf')).toBeInTheDocument()
  })
})
