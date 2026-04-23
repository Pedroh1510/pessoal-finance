import { Outlet, NavLink } from 'react-router-dom'
import { useTheme } from '../contexts/ThemeContext'

const navItems = [
  { to: '/',             label: 'Dashboard',     end: true },
  { to: '/transactions', label: 'Transações' },
  { to: '/inflation',    label: 'Inflação' },
  { to: '/patrimony',    label: 'Patrimônio' },
  { to: '/uploads',      label: 'Uploads' },
  { to: '/settings',     label: 'Configurações' },
]

export default function Layout() {
  const { theme, toggleTheme } = useTheme()

  return (
    <div style={{ display: 'flex', minHeight: '100vh', background: 'var(--color-bg)' }}>
      <nav
        style={{
          width: 220,
          padding: '1rem',
          borderRight: '1px solid var(--color-border)',
          background: 'var(--color-surface)',
          display: 'flex',
          flexDirection: 'column',
          position: 'sticky',
          top: 0,
          height: '100vh',
          overflowY: 'auto',
          flexShrink: 0,
          boxSizing: 'border-box',
        }}
      >
        <h2 style={{ margin: '0 0 1rem', color: 'var(--color-text)' }}>Finance</h2>
        <ul style={{ listStyle: 'none', padding: 0, margin: 0, flex: 1 }}>
          {navItems.map(({ to, label, end }) => (
            <li key={to} style={{ marginBottom: '0.5rem' }}>
              <NavLink
                to={to}
                end={end}
                style={({ isActive }) => ({
                  textDecoration: 'none',
                  color: isActive ? 'var(--color-accent)' : 'var(--color-text)',
                  fontWeight: isActive ? 600 : 400,
                })}
              >
                {label}
              </NavLink>
            </li>
          ))}
        </ul>

        <button
          onClick={toggleTheme}
          aria-label={`Alternar tema (atual: ${theme})`}
          style={{
            marginTop: '1rem',
            padding: '0.45rem 0.75rem',
            border: '1px solid var(--color-border-input)',
            borderRadius: '6px',
            background: 'var(--color-bg)',
            color: 'var(--color-text)',
            cursor: 'pointer',
            fontSize: '0.85rem',
            display: 'flex',
            alignItems: 'center',
            gap: '0.4rem',
          }}
        >
          {theme === 'light' ? '🌙 Tema escuro' : '☀️ Tema claro'}
        </button>
      </nav>

      <main style={{ flex: 1, padding: '1.5rem', color: 'var(--color-text)' }}>
        <Outlet />
      </main>
    </div>
  )
}
