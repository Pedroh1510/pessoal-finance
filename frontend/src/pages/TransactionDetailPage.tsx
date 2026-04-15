import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getTransaction, categorizeTransaction } from '../lib/finance'
import { getCategories } from '../lib/categories'

const BRL = (amount: number) =>
  amount.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })

const TYPE_LABELS: Record<string, string> = {
  INCOME: 'Receita',
  EXPENSE: 'Despesa',
  INTERNAL_TRANSFER: 'Transferência Interna',
}

export default function TransactionDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [selectedCategory, setSelectedCategory] = useState('')
  const [saved, setSaved] = useState(false)

  const { data: transaction, isLoading, isError } = useQuery({
    queryKey: ['transaction', id],
    queryFn: () => getTransaction(id!),
    enabled: !!id,
  })

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
  })

  const { mutate: saveCategory, isPending } = useMutation({
    mutationFn: () => categorizeTransaction(id!, selectedCategory),
    onSuccess: () => {
      setSaved(true)
      queryClient.invalidateQueries({ queryKey: ['transaction', id] })
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      setTimeout(() => setSaved(false), 2000)
    },
  })

  if (isLoading) return <p style={{ padding: '2rem' }}>Carregando...</p>
  if (isError || !transaction)
    return <p style={{ color: '#d61f69', padding: '2rem' }}>Erro ao carregar transação.</p>

  const currentCategoryId = selectedCategory || transaction.categoryId || ''

  return (
    <div style={{ maxWidth: 600 }}>
      <button
        onClick={() => navigate('/transactions')}
        style={{ ...btnStyle, marginBottom: '1.5rem' }}
      >
        ← Voltar
      </button>

      <h1 style={{ marginTop: 0, marginBottom: '1.5rem' }}>Detalhes da Transação</h1>

      <div style={cardStyle}>
        <dl style={{ display: 'grid', gridTemplateColumns: '160px 1fr', rowGap: '0.75rem', margin: 0 }}>
          <dt style={dtStyle}>Data</dt>
          <dd style={ddStyle}>{new Date(transaction.date).toLocaleDateString('pt-BR')}</dd>

          <dt style={dtStyle}>Valor</dt>
          <dd
            style={{
              ...ddStyle,
              fontWeight: 700,
              color:
                transaction.type === 'INCOME'
                  ? '#057a55'
                  : transaction.type === 'EXPENSE'
                    ? '#d61f69'
                    : '#333',
            }}
          >
            {transaction.type === 'EXPENSE' ? '-' : ''}
            {BRL(Math.abs(transaction.amount))}
          </dd>

          <dt style={dtStyle}>Tipo</dt>
          <dd style={ddStyle}>{TYPE_LABELS[transaction.type] ?? transaction.type}</dd>

          <dt style={dtStyle}>Banco</dt>
          <dd style={ddStyle}>{transaction.bankName}</dd>

          <dt style={dtStyle}>Destinatário</dt>
          <dd style={ddStyle}>{transaction.recipient ?? '—'}</dd>

          <dt style={dtStyle}>Descrição</dt>
          <dd style={ddStyle}>{transaction.description ?? '—'}</dd>

          <dt style={dtStyle}>Categoria atual</dt>
          <dd style={ddStyle}>{transaction.categoryName ?? <em>Sem categoria</em>}</dd>
        </dl>
      </div>

      <div style={{ ...cardStyle, marginTop: '1.25rem' }}>
        <h3 style={{ marginTop: 0 }}>Alterar Categoria</h3>
        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
          <select
            value={currentCategoryId}
            onChange={(e) => setSelectedCategory(e.target.value)}
            style={{
              padding: '0.45rem 0.6rem',
              border: '1px solid #d1d5db',
              borderRadius: '4px',
              fontSize: '0.9rem',
              minWidth: 180,
            }}
            aria-label="Selecionar categoria"
          >
            <option value="">Sem categoria</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>

          <button
            onClick={() => saveCategory()}
            disabled={isPending || !selectedCategory}
            style={{
              ...btnStyle,
              background: '#1a56db',
              color: '#fff',
              border: 'none',
              opacity: isPending || !selectedCategory ? 0.6 : 1,
            }}
          >
            {isPending ? 'Salvando...' : 'Salvar categoria'}
          </button>

          {saved && <span style={{ color: '#057a55', fontSize: '0.875rem' }}>Salvo!</span>}
        </div>
      </div>
    </div>
  )
}

const cardStyle: React.CSSProperties = {
  background: '#fff',
  border: '1px solid #e5e7eb',
  borderRadius: '8px',
  padding: '1.25rem',
}

const dtStyle: React.CSSProperties = {
  color: '#6b7280',
  fontSize: '0.875rem',
  fontWeight: 500,
}

const ddStyle: React.CSSProperties = {
  margin: 0,
  fontSize: '0.9rem',
  color: '#111827',
}

const btnStyle: React.CSSProperties = {
  padding: '0.45rem 1rem',
  border: '1px solid #d1d5db',
  borderRadius: '4px',
  cursor: 'pointer',
  fontSize: '0.875rem',
  background: '#fff',
}
