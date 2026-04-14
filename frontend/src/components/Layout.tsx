import { Outlet, NavLink } from 'react-router-dom'

const navItems = [
  { to: '/',             label: 'Dashboard',     end: true },
  { to: '/transactions', label: 'Transações' },
  { to: '/inflation',    label: 'Inflação' },
  { to: '/patrimony',    label: 'Patrimônio' },
  { to: '/uploads',      label: 'Uploads' },
  { to: '/settings',     label: 'Configurações' },
]

export default function Layout() {
  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      <nav style={{ width: 220, padding: '1rem', borderRight: '1px solid #eee' }}>
        <h2 style={{ margin: '0 0 1rem' }}>Finance</h2>
        <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
          {navItems.map(({ to, label, end }) => (
            <li key={to} style={{ marginBottom: '0.5rem' }}>
              <NavLink
                to={to}
                end={end}
                style={({ isActive }) => ({
                  textDecoration: 'none',
                  color: isActive ? '#1a56db' : '#333',
                  fontWeight: isActive ? 600 : 400,
                })}
              >
                {label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <main style={{ flex: 1, padding: '1.5rem' }}>
        <Outlet />
      </main>
    </div>
  )
}
