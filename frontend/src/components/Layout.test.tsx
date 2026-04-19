import { render, screen, fireEvent } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider } from '../contexts/ThemeContext'
import Layout from './Layout'

const queryClient = new QueryClient()

function renderLayout() {
  const router = createMemoryRouter([
    {
      path: '/',
      element: <Layout />,
      children: [{ index: true, element: <div>content</div> }],
    },
  ])
  return render(
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>
  )
}

describe('Layout', () => {
  it('renders all navigation links', () => {
    renderLayout()
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
    expect(screen.getByText('Transações')).toBeInTheDocument()
    expect(screen.getByText('Inflação')).toBeInTheDocument()
    expect(screen.getByText('Patrimônio')).toBeInTheDocument()
    expect(screen.getByText('Uploads')).toBeInTheDocument()
    expect(screen.getByText('Configurações')).toBeInTheDocument()
  })

  it('renders outlet content', () => {
    renderLayout()
    expect(screen.getByText('content')).toBeInTheDocument()
  })

  it('renders theme toggle button', () => {
    renderLayout()
    expect(screen.getByRole('button', { name: /tema/i })).toBeInTheDocument()
  })

  it('toggles theme label when clicked', () => {
    renderLayout()
    const btn = screen.getByRole('button', { name: /tema/i })
    fireEvent.click(btn)
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })
})
