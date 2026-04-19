import { useQuery } from '@tanstack/react-query'
import {
  PieChart,
  Pie,
  Cell,
  Tooltip,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
} from 'recharts'
import { getTransactions, TransactionDTO } from '../lib/finance'

const BRL = (amount: number) =>
  amount.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })

function getMonthLabel(monthOffset: number): string {
  const d = new Date()
  d.setMonth(d.getMonth() + monthOffset)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

const COLORS = ['#1a56db', '#e3a008', '#057a55', '#d61f69', '#5850ec']

function aggregateByCategory(transactions: TransactionDTO[]) {
  const map: Record<string, { name: string; value: number }> = {}
  for (const t of transactions) {
    if (t.type !== 'EXPENSE') continue
    const key = t.categoryId ?? '__uncategorized__'
    const label = t.categoryName ?? 'Sem categoria'
    if (!map[key]) map[key] = { name: label, value: 0 }
    map[key].value += Math.abs(t.amount)
  }
  return Object.values(map)
    .sort((a, b) => b.value - a.value)
    .slice(0, 5)
}

export default function DashboardPage() {
  const currentMonth = getMonthLabel(0)

  const { data: currentMonthTx = [], isLoading: loadingCurrent } = useQuery({
    queryKey: ['transactions', 'dashboard', currentMonth],
    queryFn: async () => {
      const page = await getTransactions({ month: currentMonth, size: 1000 })
      return page.content
    },
  })

  const last12Months = Array.from({ length: 12 }, (_, i) => getMonthLabel(i - 11))

  const { data: monthlyData = [], isLoading: loadingMonthly } = useQuery({
    queryKey: ['transactions', 'dashboard', 'monthly'],
    queryFn: async () => {
      const results = await Promise.all(
        last12Months.map(async (month) => {
          const page = await getTransactions({ month, size: 1000 })
          let income = 0
          let expense = 0
          for (const t of page.content) {
            if (t.type === 'INCOME') income += t.amount
            if (t.type === 'EXPENSE') expense += Math.abs(t.amount)
          }
          const [year, m] = month.split('-')
          const label = new Date(Number(year), Number(m) - 1).toLocaleString('pt-BR', {
            month: 'short',
            year: '2-digit',
          })
          return { month: label, Receita: income, Despesa: expense }
        }),
      )
      return results
    },
  })

  const income = currentMonthTx
    .filter((t) => t.type === 'INCOME')
    .reduce((sum, t) => sum + t.amount, 0)
  const expense = currentMonthTx
    .filter((t) => t.type === 'EXPENSE')
    .reduce((sum, t) => sum + Math.abs(t.amount), 0)
  const balance = income - expense

  const topCategories = aggregateByCategory(currentMonthTx)

  if (loadingCurrent || loadingMonthly) {
    return <p style={{ padding: '2rem' }}>Carregando dashboard...</p>
  }

  return (
    <div>
      <h1 style={{ marginBottom: '1.5rem' }}>Dashboard</h1>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '1rem', marginBottom: '2rem' }}>
        <div style={cardStyle}>
          <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', marginBottom: '0.25rem' }}>Receita</div>
          <div style={{ fontSize: '1.4rem', fontWeight: 700, color: 'var(--color-success)' }}>{BRL(income)}</div>
        </div>
        <div style={cardStyle}>
          <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', marginBottom: '0.25rem' }}>Despesa</div>
          <div style={{ fontSize: '1.4rem', fontWeight: 700, color: 'var(--color-danger)' }}>{BRL(expense)}</div>
        </div>
        <div style={cardStyle}>
          <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', marginBottom: '0.25rem' }}>Saldo do mês</div>
          <div
            style={{
              fontSize: '1.4rem',
              fontWeight: 700,
              color: balance >= 0 ? 'var(--color-success)' : 'var(--color-danger)',
            }}
          >
            {BRL(balance)}
          </div>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', marginBottom: '1.5rem' }}>
        <div style={cardStyle}>
          <h3 style={{ marginTop: 0, marginBottom: '1rem' }}>Top 5 Categorias (Despesas)</h3>
          {topCategories.length === 0 ? (
            <p style={{ color: 'var(--color-text-muted)' }}>Nenhum dado disponível</p>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie
                  data={topCategories}
                  dataKey="value"
                  nameKey="name"
                  cx="50%"
                  cy="50%"
                  outerRadius={80}
                  label={({ name, percent }) =>
                    `${name} ${(percent * 100).toFixed(0)}%`
                  }
                >
                  {topCategories.map((_, index) => (
                    <Cell key={index} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip formatter={(value: number) => BRL(value)} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>

        <div style={cardStyle}>
          <h3 style={{ marginTop: 0, marginBottom: '0.5rem' }}>Patrimônio</h3>
          <p style={{ color: 'var(--color-text-muted)', fontStyle: 'italic' }}>Em breve</p>
        </div>
      </div>

      <div style={cardStyle}>
        <h3 style={{ marginTop: 0, marginBottom: '1rem' }}>Receita vs Despesa — 12 meses</h3>
        <ResponsiveContainer width="100%" height={280}>
          <BarChart data={monthlyData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="month" tick={{ fontSize: 11 }} />
            <YAxis tickFormatter={(v) => `R$ ${(v / 1000).toFixed(0)}k`} />
            <Tooltip formatter={(value: number) => BRL(value)} />
            <Legend />
            <Bar dataKey="Receita" fill="var(--color-success)" />
            <Bar dataKey="Despesa" fill="var(--color-danger)" />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

const cardStyle: React.CSSProperties = {
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: '8px',
  padding: '1.25rem',
}
