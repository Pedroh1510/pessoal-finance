import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
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
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
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
})
