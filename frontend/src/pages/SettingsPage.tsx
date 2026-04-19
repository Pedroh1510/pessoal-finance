import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import {
  getCategories,
  createCategory,
  updateCategory,
  deleteCategory,
  CategoryDTO,
} from '../lib/categories'
import {
  getRecipientRules,
  createRecipientRule,
  deleteRecipientRule,
  getInternalAccountRules,
  createInternalAccountRule,
  deleteInternalAccountRule,
  reprocessTransactions,
} from '../lib/finance'

type Tab = 'categories' | 'recipient-rules' | 'internal-accounts' | 'reprocess'

export default function SettingsPage() {
  const [activeTab, setActiveTab] = useState<Tab>('categories')

  const tabs: { key: Tab; label: string }[] = [
    { key: 'categories', label: 'Categorias' },
    { key: 'recipient-rules', label: 'Regras de Destinatário' },
    { key: 'internal-accounts', label: 'Contas Internas' },
    { key: 'reprocess' as Tab, label: 'Reprocessar' },
  ]

  return (
    <div>
      <h1 style={{ marginBottom: '1.25rem' }}>Configurações</h1>

      <ul
        role="tablist"
        style={{ display: 'flex', gap: '0', listStyle: 'none', padding: 0, margin: '0 0 1.5rem', borderBottom: '2px solid var(--color-border)' }}
      >
        {tabs.map((t) => (
          <li key={t.key} role="presentation">
            <button
              role="tab"
              aria-selected={activeTab === t.key}
              onClick={() => setActiveTab(t.key)}
              style={{
                padding: '0.6rem 1.25rem',
                border: 'none',
                borderBottom: activeTab === t.key ? '2px solid var(--color-accent)' : '2px solid transparent',
                marginBottom: '-2px',
                background: 'transparent',
                cursor: 'pointer',
                fontWeight: activeTab === t.key ? 600 : 400,
                color: activeTab === t.key ? 'var(--color-accent)' : 'var(--color-text-muted)',
                fontSize: '0.9rem',
              }}
            >
              {t.label}
            </button>
          </li>
        ))}
      </ul>

      {activeTab === 'categories' && <CategoriesTab />}
      {activeTab === 'recipient-rules' && <RecipientRulesTab />}
      {activeTab === 'internal-accounts' && <InternalAccountsTab />}
      {activeTab === 'reprocess' && <ReprocessTab />}
    </div>
  )
}

/* ─── Categories Tab ─── */

interface CategoryFormValues {
  name: string
  color: string
}

function CategoriesTab() {
  const queryClient = useQueryClient()
  const [editingId, setEditingId] = useState<string | null>(null)
  const { register, handleSubmit, reset, setValue } = useForm<CategoryFormValues>({
    defaultValues: { name: '', color: '#1a56db' },
  })

  const { data: categories = [], isLoading } = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
  })

  const { mutate: create, isPending: creating } = useMutation({
    mutationFn: ({ name, color }: CategoryFormValues) => createCategory(name, color),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] })
      reset()
    },
  })

  const { mutate: update, isPending: updating } = useMutation({
    mutationFn: ({ id, name, color }: { id: string } & CategoryFormValues) =>
      updateCategory(id, name, color),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] })
      setEditingId(null)
      reset()
    },
  })

  const { mutate: remove } = useMutation({
    mutationFn: deleteCategory,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['categories'] }),
  })

  const onSubmit = (values: CategoryFormValues) => {
    if (editingId) {
      update({ id: editingId, ...values })
    } else {
      create(values)
    }
  }

  const startEdit = (cat: CategoryDTO) => {
    setEditingId(cat.id)
    setValue('name', cat.name)
    setValue('color', cat.color)
  }

  const cancelEdit = () => {
    setEditingId(null)
    reset()
  }

  if (isLoading) return <p>Carregando...</p>

  return (
    <div>
      <form onSubmit={handleSubmit(onSubmit)} style={formRowStyle}>
        <input
          {...register('name', { required: true })}
          placeholder="Nome da categoria"
          style={inputStyle}
          aria-label="Nome da categoria"
        />
        <input
          type="color"
          {...register('color')}
          style={{ width: 40, height: 36, padding: '2px', border: '1px solid var(--color-border-input)', borderRadius: 4, cursor: 'pointer' }}
          aria-label="Cor da categoria"
        />
        <button type="submit" disabled={creating || updating} style={primaryBtnStyle}>
          {editingId ? 'Atualizar' : 'Adicionar'}
        </button>
        {editingId && (
          <button type="button" onClick={cancelEdit} style={secondaryBtnStyle}>
            Cancelar
          </button>
        )}
      </form>

      <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
        {categories.map((cat) => (
          <li key={cat.id} style={listItemStyle}>
            <span
              style={{
                display: 'inline-block',
                width: 16,
                height: 16,
                borderRadius: '50%',
                background: cat.color,
                marginRight: '0.5rem',
                flexShrink: 0,
              }}
            />
            <span style={{ flex: 1 }}>{cat.name}</span>
            {cat.isSystem && (
              <span style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)', marginRight: '0.5rem' }}>
                sistema
              </span>
            )}
            <button
              onClick={() => startEdit(cat)}
              style={secondaryBtnStyle}
              disabled={cat.isSystem}
            >
              Editar
            </button>
            <button
              onClick={() => remove(cat.id)}
              style={{ ...secondaryBtnStyle, color: 'var(--color-danger)' }}
              disabled={cat.isSystem}
            >
              Excluir
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

/* ─── Recipient Rules Tab ─── */

interface RecipientRuleFormValues {
  recipientPattern: string
  categoryId: string
}

function RecipientRulesTab() {
  const queryClient = useQueryClient()
  const { register, handleSubmit, reset } = useForm<RecipientRuleFormValues>()

  const { data: rules = [], isLoading } = useQuery({
    queryKey: ['recipient-rules'],
    queryFn: getRecipientRules,
  })

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
  })

  const { mutate: create, isPending } = useMutation({
    mutationFn: ({ recipientPattern, categoryId }: RecipientRuleFormValues) =>
      createRecipientRule(recipientPattern, categoryId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recipient-rules'] })
      reset()
    },
  })

  const { mutate: remove } = useMutation({
    mutationFn: deleteRecipientRule,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['recipient-rules'] }),
  })

  if (isLoading) return <p>Carregando...</p>

  return (
    <div>
      <form onSubmit={handleSubmit((v) => create(v))} style={formRowStyle}>
        <input
          {...register('recipientPattern', { required: true })}
          placeholder="Padrão do destinatário"
          style={inputStyle}
          aria-label="Padrão do destinatário"
        />
        <select
          {...register('categoryId', { required: true })}
          style={inputStyle}
          aria-label="Categoria"
        >
          <option value="">Selecionar categoria</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
        <button type="submit" disabled={isPending} style={primaryBtnStyle}>
          Adicionar regra
        </button>
      </form>

      {rules.length === 0 ? (
        <p style={{ color: 'var(--color-text-muted)' }}>Nenhuma regra cadastrada.</p>
      ) : (
        <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
          {rules.map((rule) => (
            <li key={rule.id} style={listItemStyle}>
              <span style={{ flex: 1 }}>
                <strong>{rule.recipientPattern}</strong> → {rule.categoryName}
              </span>
              <button
                onClick={() => remove(rule.id)}
                style={{ ...secondaryBtnStyle, color: 'var(--color-danger)' }}
              >
                Excluir
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/* ─── Internal Accounts Tab ─── */

interface InternalAccountFormValues {
  identifier: string
  type: string
}

function InternalAccountsTab() {
  const queryClient = useQueryClient()
  const { register, handleSubmit, reset } = useForm<InternalAccountFormValues>()

  const { data: rules = [], isLoading } = useQuery({
    queryKey: ['internal-account-rules'],
    queryFn: getInternalAccountRules,
  })

  const { mutate: create, isPending } = useMutation({
    mutationFn: ({ identifier, type }: InternalAccountFormValues) =>
      createInternalAccountRule(identifier, type),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['internal-account-rules'] })
      reset()
    },
  })

  const { mutate: remove } = useMutation({
    mutationFn: deleteInternalAccountRule,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['internal-account-rules'] }),
  })

  if (isLoading) return <p>Carregando...</p>

  return (
    <div>
      <form onSubmit={handleSubmit((v) => create(v))} style={formRowStyle}>
        <input
          {...register('identifier', { required: true })}
          placeholder="Identificador (nome, CPF, CNPJ)"
          style={inputStyle}
          aria-label="Identificador"
        />
        <select
          {...register('type', { required: true })}
          style={inputStyle}
          aria-label="Tipo de identificador"
        >
          <option value="">Tipo</option>
          <option value="NAME">Nome</option>
          <option value="CPF">CPF</option>
          <option value="CNPJ">CNPJ</option>
        </select>
        <button type="submit" disabled={isPending} style={primaryBtnStyle}>
          Adicionar conta
        </button>
      </form>

      {rules.length === 0 ? (
        <p style={{ color: 'var(--color-text-muted)' }}>Nenhuma conta interna cadastrada.</p>
      ) : (
        <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
          {rules.map((rule) => (
            <li key={rule.id} style={listItemStyle}>
              <span style={{ flex: 1 }}>
                <strong>{rule.identifier}</strong>{' '}
                <span style={{ color: 'var(--color-text-muted)', fontSize: '0.8rem' }}>({rule.type})</span>
              </span>
              <button
                onClick={() => remove(rule.id)}
                style={{ ...secondaryBtnStyle, color: 'var(--color-danger)' }}
              >
                Excluir
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/* ─── Reprocess Tab ─── */

interface ReprocessResultState {
  categorized: number
  typeChanged: number
}

function ReprocessTab() {
  const queryClient = useQueryClient()
  const [result, setResult] = useState<ReprocessResultState | null>(null)
  const [error, setError] = useState<string | null>(null)

  const { mutate: run, isPending } = useMutation({
    mutationFn: reprocessTransactions,
    onSuccess: (data) => {
      setError(null)
      setResult(data)
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
    },
    onError: (err: unknown) => {
      const message = err instanceof Error ? err.message : 'Erro ao reprocessar transações.'
      setError(message)
    },
  })

  return (
    <div style={{ maxWidth: 480 }}>
      <p style={{ color: 'var(--color-text-muted)', fontSize: '0.9rem', marginBottom: '1.25rem' }}>
        Reaplica as regras de categoria e de transferência interna em todas as transações
        existentes. Transações sem categoria recebem a categoria conforme as regras de
        destinatário. Transações de receita ou despesa que se enquadrem nas regras de contas
        internas são reclassificadas como Transferência Interna.
      </p>

      <button
        onClick={() => { setResult(null); setError(null); run() }}
        disabled={isPending}
        style={{
          ...primaryBtnStyle,
          opacity: isPending ? 0.65 : 1,
          cursor: isPending ? 'not-allowed' : 'pointer',
        }}
      >
        {isPending ? 'Processando...' : 'Reprocessar transações'}
      </button>

      {error && (
        <p role="alert" style={{ marginTop: '1rem', color: 'var(--color-danger)', fontSize: '0.9rem' }}>
          {error}
        </p>
      )}

      {result && (
        <p style={{ marginTop: '1.25rem', fontSize: '0.9rem', color: 'var(--color-text)' }}>
          <strong>{result.categorized}</strong> transação(ões) categorizada(s).{' '}
          <strong>{result.typeChanged}</strong> tipo(s) alterado(s) para Transferência Interna.
        </p>
      )}
    </div>
  )
}

/* ─── Shared styles ─── */

const formRowStyle: React.CSSProperties = {
  display: 'flex',
  gap: '0.6rem',
  marginBottom: '1.25rem',
  flexWrap: 'wrap',
  alignItems: 'center',
}

const inputStyle: React.CSSProperties = {
  padding: '0.45rem 0.6rem',
  border: '1px solid var(--color-border-input)',
  borderRadius: '4px',
  fontSize: '0.9rem',
  minWidth: 160,
}

const primaryBtnStyle: React.CSSProperties = {
  padding: '0.45rem 1rem',
  background: 'var(--color-accent)',
  color: '#fff',
  border: 'none',
  borderRadius: '4px',
  cursor: 'pointer',
  fontSize: '0.875rem',
}

const secondaryBtnStyle: React.CSSProperties = {
  padding: '0.35rem 0.75rem',
  background: 'var(--color-surface)',
  color: 'var(--color-text)',
  border: '1px solid var(--color-border-input)',
  borderRadius: '4px',
  cursor: 'pointer',
  fontSize: '0.8rem',
}

const listItemStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '0.5rem',
  padding: '0.6rem 0',
  borderBottom: '1px solid var(--color-border-subtle)',
}
