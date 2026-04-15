import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import UploadsPage from './UploadsPage'

vi.mock('../lib/finance', () => ({
  uploadStatement: vi.fn().mockResolvedValue({ total: 10, internalTransfers: 2, uncategorized: 3 }),
}))

function createWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
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
    expect(screen.getByLabelText(/selecionar arquivo pdf/i)).toBeInTheDocument()
  })

  it('renders submit button', () => {
    render(<UploadsPage />, { wrapper: createWrapper() })
    expect(screen.getByRole('button', { name: /enviar extrato/i })).toBeInTheDocument()
  })

  it('submit button is disabled when no file selected', () => {
    render(<UploadsPage />, { wrapper: createWrapper() })
    expect(screen.getByRole('button', { name: /enviar extrato/i })).toBeDisabled()
  })
})
