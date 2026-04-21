import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, Legend, ResponsiveContainer,
} from 'recharts'
import {
  getInflationComparison,
  getInflationItems,
  uploadInflation,
  type InflationUploadResult,
  type MarketItemDTO,
} from '../lib/inflation'

const cardStyle: React.CSSProperties = {
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: '8px',
  padding: '1.5rem',
}

const labelStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '0.3rem',
  fontSize: '0.85rem',
  color: 'var(--color-text-muted)',
  fontWeight: 500,
}

const inputStyle: React.CSSProperties = {
  padding: '0.45rem 0.6rem',
  border: '1px solid var(--color-border-input)',
  borderRadius: '4px',
  fontSize: '0.9rem',
  background: 'var(--color-surface)',
  color: 'var(--color-text)',
}

export default function InflationPage() {
  const queryClient = useQueryClient()

  const [files, setFiles] = useState<File[]>([])
  const inputRef = useRef<HTMLInputElement>(null)
  const [uploadMessage, setUploadMessage] = useState<string | null>(null)
  const [isError, setIsError] = useState(false)

  const [ncmFilter, setNcmFilter] = useState('')
  const [periodFilter, setPeriodFilter] = useState('')
  const [fromPeriod, setFromPeriod] = useState('')
  const [toPeriod, setToPeriod] = useState('')

  const canShowChart = !!ncmFilter && !!fromPeriod && !!toPeriod

  const uploadMutation = useMutation({
    mutationFn: async (filesToUpload: File[]) => {
      let total: InflationUploadResult = { purchasesCreated: 0, purchasesSkipped: 0, itemsImported: 0 }
      for (const f of filesToUpload) {
        const result = await uploadInflation(f)
        total = {
          purchasesCreated: total.purchasesCreated + result.purchasesCreated,
          purchasesSkipped: total.purchasesSkipped + result.purchasesSkipped,
          itemsImported: total.itemsImported + result.itemsImported,
        }
      }
      return total
    },
    onSuccess: (result) => {
      const skippedMsg = result.purchasesSkipped > 0
        ? ` (${result.purchasesSkipped} já existia${result.purchasesSkipped > 1 ? 'm' : ''})`
        : ''
      setUploadMessage(
        `${result.purchasesCreated} nota${result.purchasesCreated !== 1 ? 's' : ''} fiscal importada${result.purchasesCreated !== 1 ? 's' : ''}${skippedMsg}, ${result.itemsImported} itens.`
      )
      setIsError(false)
      setFiles([])
      if (inputRef.current) inputRef.current.value = ''
      queryClient.invalidateQueries({ queryKey: ['inflation-items'] })
    },
    onError: () => {
      setUploadMessage('Erro ao importar planilha.')
      setIsError(true)
    },
  })

  const itemsQuery = useQuery({
    queryKey: ['inflation-items', ncmFilter, periodFilter],
    queryFn: () => getInflationItems({
      ncm: ncmFilter || undefined,
      period: periodFilter || undefined,
    }),
  })

  const comparisonQuery = useQuery({
    queryKey: ['inflation-comparison', ncmFilter, fromPeriod, toPeriod],
    queryFn: () => getInflationComparison({ ncm: ncmFilter, from: fromPeriod, to: toPeriod }),
    enabled: canShowChart,
  })

  const items: MarketItemDTO[] = itemsQuery.data ?? []

  const handleUpload = (e: React.FormEvent) => {
    e.preventDefault()
    if (files.length === 0) return
    setUploadMessage(null)
    uploadMutation.mutate(files)
  }

  return (
    <div style={{ maxWidth: 960 }}>
      <h1 style={{ marginBottom: '1.5rem' }}>Inflação</h1>

      {/* Upload */}
      <div style={{ ...cardStyle, marginBottom: '1.5rem' }}>
        <h2 style={{ margin: '0 0 1rem', fontSize: '1rem' }}>Importar compras (XLS/XLSX)</h2>
        <p style={{ margin: '0 0 1rem', fontSize: '0.85rem', color: 'var(--color-text-muted)' }}>
          Arquivo exportado pelo app de notas fiscais. Loja e data são lidos do próprio arquivo.
        </p>
        <form onSubmit={handleUpload}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <label style={labelStyle}>
              Arquivo XLS / XLSX
              <input
                ref={inputRef}
                type="file"
                multiple
                accept=".xls,.xlsx,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                onChange={(e) => setFiles(Array.from(e.target.files ?? []))}
                aria-label="Arquivo XLS / XLSX"
              />
              {files.length > 0 && (
                <p style={{ margin: 0, fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
                  {files.length} arquivo{files.length !== 1 ? 's' : ''} selecionado{files.length !== 1 ? 's' : ''}
                </p>
              )}
            </label>

            {uploadMessage && (
              <p
                role="status"
                style={{
                  margin: 0,
                  fontSize: '0.875rem',
                  color: isError ? 'var(--color-danger)' : 'var(--color-success)',
                }}
              >
                {uploadMessage}
              </p>
            )}

            <button
              type="submit"
              disabled={files.length === 0 || uploadMutation.isPending}
              style={{
                padding: '0.6rem 1.5rem',
                background: 'var(--color-accent)',
                color: '#fff',
                border: 'none',
                borderRadius: '4px',
                cursor: files.length === 0 || uploadMutation.isPending ? 'not-allowed' : 'pointer',
                fontSize: '0.9rem',
                opacity: files.length === 0 || uploadMutation.isPending ? 0.65 : 1,
                alignSelf: 'flex-start',
              }}
            >
              {uploadMutation.isPending
                ? 'Importando...'
                : files.length > 1
                  ? `Importar ${files.length} planilhas`
                  : 'Importar planilha'}
            </button>
          </div>
        </form>
      </div>

      {/* Filtros */}
      <div style={{ ...cardStyle, marginBottom: '1.5rem' }}>
        <h2 style={{ margin: '0 0 1rem', fontSize: '1rem' }}>Filtros</h2>
        <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
          <label style={labelStyle}>
            NCM
            <input
              style={{ ...inputStyle, width: 140 }}
              value={ncmFilter}
              onChange={(e) => setNcmFilter(e.target.value)}
              placeholder="Ex: 10059010"
              aria-label="NCM"
            />
          </label>

          <label style={labelStyle}>
            Período
            <input
              type="month"
              style={{ ...inputStyle, width: 160 }}
              value={periodFilter}
              onChange={(e) => setPeriodFilter(e.target.value)}
              aria-label="Período"
            />
          </label>

          <label style={labelStyle}>
            De (comparativo)
            <input
              type="month"
              style={{ ...inputStyle, width: 160 }}
              value={fromPeriod}
              onChange={(e) => setFromPeriod(e.target.value)}
              aria-label="De"
            />
          </label>

          <label style={labelStyle}>
            Até (comparativo)
            <input
              type="month"
              style={{ ...inputStyle, width: 160 }}
              value={toPeriod}
              onChange={(e) => setToPeriod(e.target.value)}
              aria-label="Até"
            />
          </label>
        </div>
      </div>

      {/* Gráfico de comparativo */}
      {canShowChart && comparisonQuery.data && comparisonQuery.data.prices.length > 0 && (
        <div style={{ ...cardStyle, marginBottom: '1.5rem' }}>
          <h2 style={{ margin: '0 0 1rem', fontSize: '1rem' }}>
            Evolução de preço — NCM {ncmFilter}
            {comparisonQuery.data.description && ` · ${comparisonQuery.data.description}`}
          </h2>
          <ResponsiveContainer width="100%" height={280}>
            <LineChart data={comparisonQuery.data.prices}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
              <XAxis dataKey="period" tick={{ fontSize: 12 }} />
              <YAxis
                tick={{ fontSize: 12 }}
                tickFormatter={(v: number) => `R$${v.toFixed(2)}`}
              />
              <Tooltip formatter={(value: number) => [`R$ ${value.toFixed(2)}`, 'Preço unit.']} />
              <Legend />
              <Line
                type="monotone"
                dataKey="unitPrice"
                name="Preço unitário"
                stroke="var(--color-accent)"
                strokeWidth={2}
                dot={{ r: 4 }}
                activeDot={{ r: 6 }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Tabela de itens */}
      <div style={cardStyle}>
        <h2 style={{ margin: '0 0 1rem', fontSize: '1rem' }}>Itens</h2>
        {itemsQuery.isLoading && (
          <p style={{ color: 'var(--color-text-muted)', fontSize: '0.875rem' }}>Carregando...</p>
        )}
        {!itemsQuery.isLoading && items.length === 0 && (
          <p style={{ color: 'var(--color-text-muted)', fontSize: '0.875rem' }}>
            Nenhum item encontrado. Importe uma planilha ou ajuste os filtros.
          </p>
        )}
        {items.length > 0 && (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
              <thead>
                <tr>
                  {['Período', 'Loja', 'NCM', 'Descrição', 'Qtd', 'Preço Unit.', 'Total'].map((h) => (
                    <th
                      key={h}
                      style={{
                        textAlign: 'left',
                        padding: '0.5rem 0.75rem',
                        borderBottom: '1px solid var(--color-border)',
                        color: 'var(--color-text-muted)',
                        fontWeight: 600,
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id} style={{ borderBottom: '1px solid var(--color-border-subtle)' }}>
                    <td style={{ padding: '0.5rem 0.75rem' }}>{item.period}</td>
                    <td style={{ padding: '0.5rem 0.75rem' }}>{item.emitente}</td>
                    <td style={{ padding: '0.5rem 0.75rem', fontFamily: 'monospace' }}>{item.ncm}</td>
                    <td style={{ padding: '0.5rem 0.75rem' }}>{item.description}</td>
                    <td style={{ padding: '0.5rem 0.75rem', textAlign: 'right' }}>
                      {item.quantity.toFixed(3)}
                    </td>
                    <td style={{ padding: '0.5rem 0.75rem', textAlign: 'right' }}>
                      R$ {item.unitPrice.toFixed(2)}
                    </td>
                    <td style={{ padding: '0.5rem 0.75rem', textAlign: 'right' }}>
                      R$ {item.totalPrice.toFixed(2)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
