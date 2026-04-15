import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { getTransactions, BankName, TransactionType } from '../lib/finance'
import { getCategories } from '../lib/categories'

const BRL = (amount: number) =>
  amount.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })

const TYPE_LABELS: Record<TransactionType, string> = {
  INCOME: 'Receita',
  EXPENSE: 'Despesa',
  INTERNAL_TRANSFER: 'Transferência Interna',
}

export default function TransactionsPage() {
  const navigate = useNavigate()
  const now = new Date()
  const defaultMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`

  const [month, setMonth] = useState(defaultMonth)
  const [bank, setBank] = useState<BankName | ''>('')
  const [categoryId, setCategoryId] = useState('')
  const [type, setType] = useState<TransactionType | ''>('')
  const [page, setPage] = useState(0)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['transactions', month, bank, categoryId, type, page],
    queryFn: () =>
      getTransactions({
        month,
        bank: bank || undefined,
        categoryId: categoryId || undefined,
        type: type || undefined,
        page,
        size: 20,
      }),
  })

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
  })

  const handleFilterChange = () => setPage(0)

  return (
    <div>
      <h1 style={{ marginBottom: '1.25rem' }}>Transações</h1>

      <div
        style={{
          display: 'flex',
          gap: '0.75rem',
          flexWrap: 'wrap',
          marginBottom: '1.25rem',
          alignItems: 'flex-end',
        }}
      >
        <label style={labelStyle}>
          Mês
          <input
            type="month"
            value={month}
            onChange={(e) => { setMonth(e.target.value); handleFilterChange() }}
            style={inputStyle}
          />
        </label>

        <label style={labelStyle}>
          Banco
          <select
            value={bank}
            onChange={(e) => { setBank(e.target.value as BankName | ''); handleFilterChange() }}
            style={inputStyle}
          >
            <option value="">Todos</option>
            <option value="NUBANK">Nubank</option>
            <option value="NEON">Neon</option>
            <option value="INTER">Inter</option>
          </select>
        </label>

        <label style={labelStyle}>
          Categoria
          <select
            value={categoryId}
            onChange={(e) => { setCategoryId(e.target.value); handleFilterChange() }}
            style={inputStyle}
          >
            <option value="">Todas</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>

        <label style={labelStyle}>
          Tipo
          <select
            value={type}
            onChange={(e) => { setType(e.target.value as TransactionType | ''); handleFilterChange() }}
            style={inputStyle}
          >
            <option value="">Todos</option>
            <option value="INCOME">Receita</option>
            <option value="EXPENSE">Despesa</option>
            <option value="INTERNAL_TRANSFER">Transferência Interna</option>
          </select>
        </label>
      </div>

      {isLoading && <p>Carregando...</p>}
      {isError && <p style={{ color: '#d61f69' }}>Erro ao carregar transações.</p>}

      {data && (
        <>
          <div style={{ overflowX: 'auto' }}>
            <table style={tableStyle}>
              <thead>
                <tr>
                  {['Data', 'Valor', 'Destinatário', 'Descrição', 'Categoria', 'Tipo'].map(
                    (h) => (
                      <th key={h} style={thStyle}>
                        {h}
                      </th>
                    ),
                  )}
                </tr>
              </thead>
              <tbody>
                {data.content.length === 0 ? (
                  <tr>
                    <td colSpan={6} style={{ textAlign: 'center', padding: '2rem', color: '#888' }}>
                      Nenhuma transação encontrada
                    </td>
                  </tr>
                ) : (
                  data.content.map((t) => (
                    <tr
                      key={t.transactionId}
                      onClick={() => navigate(`/transactions/${t.transactionId}`)}
                      style={{ cursor: 'pointer' }}
                      onMouseEnter={(e) =>
                        ((e.currentTarget as HTMLTableRowElement).style.background = '#f9fafb')
                      }
                      onMouseLeave={(e) =>
                        ((e.currentTarget as HTMLTableRowElement).style.background = '')
                      }
                    >
                      <td style={tdStyle}>{new Date(t.date).toLocaleDateString('pt-BR')}</td>
                      <td
                        style={{
                          ...tdStyle,
                          color:
                            t.type === 'INCOME'
                              ? '#057a55'
                              : t.type === 'EXPENSE'
                                ? '#d61f69'
                                : '#333',
                          fontWeight: 600,
                        }}
                      >
                        {t.type === 'EXPENSE' ? '-' : ''}
                        {BRL(Math.abs(t.amount))}
                      </td>
                      <td style={tdStyle}>{t.recipient ?? '—'}</td>
                      <td style={tdStyle}>{t.description ?? '—'}</td>
                      <td style={tdStyle}>{t.categoryName ?? <em>Sem categoria</em>}</td>
                      <td style={tdStyle}>{TYPE_LABELS[t.type]}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginTop: '1rem',
            }}
          >
            <span style={{ color: '#666', fontSize: '0.875rem' }}>
              {data.totalElements} transações · página {data.number + 1} de{' '}
              {data.totalPages || 1}
            </span>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                style={btnStyle}
              >
                Anterior
              </button>
              <button
                onClick={() => setPage((p) => p + 1)}
                disabled={page + 1 >= (data.totalPages || 1)}
                style={btnStyle}
              >
                Próxima
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}

const labelStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '0.25rem',
  fontSize: '0.8rem',
  color: '#555',
}

const inputStyle: React.CSSProperties = {
  padding: '0.4rem 0.5rem',
  border: '1px solid #d1d5db',
  borderRadius: '4px',
  fontSize: '0.9rem',
}

const tableStyle: React.CSSProperties = {
  width: '100%',
  borderCollapse: 'collapse',
  fontSize: '0.875rem',
}

const thStyle: React.CSSProperties = {
  textAlign: 'left',
  padding: '0.6rem 0.75rem',
  borderBottom: '2px solid #e5e7eb',
  color: '#374151',
  fontWeight: 600,
  whiteSpace: 'nowrap',
}

const tdStyle: React.CSSProperties = {
  padding: '0.6rem 0.75rem',
  borderBottom: '1px solid #f3f4f6',
}

const btnStyle: React.CSSProperties = {
  padding: '0.35rem 0.75rem',
  border: '1px solid #d1d5db',
  borderRadius: '4px',
  cursor: 'pointer',
  fontSize: '0.85rem',
  background: '#fff',
}
