import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, beforeEach } from 'vitest'
import { ThemeProvider, useTheme } from './ThemeContext'

function Toggle() {
  const { theme, toggleTheme } = useTheme()
  return (
    <button onClick={toggleTheme}>
      {theme}
    </button>
  )
}

describe('ThemeContext', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
  })

  it('starts with light theme by default', () => {
    render(<ThemeProvider><Toggle /></ThemeProvider>)
    expect(screen.getByRole('button')).toHaveTextContent('light')
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')
  })

  it('toggles to dark theme on click', () => {
    render(<ThemeProvider><Toggle /></ThemeProvider>)
    fireEvent.click(screen.getByRole('button'))
    expect(screen.getByRole('button')).toHaveTextContent('dark')
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })

  it('persists theme to localStorage', () => {
    render(<ThemeProvider><Toggle /></ThemeProvider>)
    fireEvent.click(screen.getByRole('button'))
    expect(localStorage.getItem('theme')).toBe('dark')
  })

  it('reads theme from localStorage on mount', () => {
    localStorage.setItem('theme', 'dark')
    render(<ThemeProvider><Toggle /></ThemeProvider>)
    expect(screen.getByRole('button')).toHaveTextContent('dark')
  })
})
