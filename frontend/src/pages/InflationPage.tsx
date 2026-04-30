import { useRef, useState, useEffect, type CSSProperties, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, Legend, ResponsiveContainer,
} from 'recharts'
import {
  getInflationComparison,
  getInflationItems,
  uploadInflation,
  type MarketItemDTO,
} from '../lib/inflation'
import { getJob, isTerminal } from '../lib/jobs'

const cardStyle: CSSProperties = {
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: '8px',
  padding: '1.5rem',
}

const labelStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '0.3rem',
  fontSize: '0.85rem',
  color: 'var(--color-text-muted)',
  fontWeight: 500,
}

const inputStyle: CSSProperties = {
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
  const [uploadProgress, setUploadProgress] = useState<{ current: number; total: number } | null>(null)

  const [ncmFilter, setNcmFilter] = useState('')
  const [descriptionFilter, setDescriptionFilter] = useState('')
  const [periodFilter, setPeriodFilter] = useState('')
  const [fromPeriod, setFromPeriod] = useState('')
  const [toPeriod, setToPeriod] = useState('')

  const [jobIds, setJobIds] = useState<string[]>([])
  const [jobResults, setJobResults] = useState<import('../lib/jobs').JobResponse[]>([])
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const jobIdsRef = useRef(jobIds)
  const jobResultsRef = useRef(jobResults)

  useEffect(() => { jobIdsRef.current = jobIds }, [jobIds])
  useEffect(() => { jobResultsRef.current = jobResults }, [jobResults])

  const canShowChart = !!(ncmFilter || descriptionFilter) && !!fromPeriod && !!toPeriod

  useEffect(() => {
    pollingRef.current = setInterval(async () => {
      const pending = jobIdsRef.current.filter(
        (id) => !jobResultsRef.current.find((j) => j.id === id && isTerminal(j.status))
      )
      for (const id of pending) {
        try {
          const job = await getJob(id)
          setJobResults((prev) => [...prev.filter((j) => j.id !== id), job])
          if (isTerminal(job.status)) {
            queryClient.invalidateQueries({ queryKey: ['inflation-items'] })
          }
        } catch { /* ignore polling errors */ }
      }
    }, 2000)
    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current)
    }
  }, [queryClient])

  const uploadMutation = useMutation({
    mutationFn: async (filesToUpload: File[]) => {
      const ids: string[] = []
      for (let i = 0; i < filesToUpload.length; i++) {
        setUploadProgress({ current: i + 1, total: filesToUpload.length })
        const { jobId } = await uploadInflation(filesToUpload[i])
        ids.push(jobId)
      }
      return ids
    },
    onSuccess: (ids) => {
      setJobIds((prev) => [...prev, ...ids])
      setUploadMessage(`${ids.length} upload(s) enviado(s). Processando em background…`)
      setIsError(false)
      setUploadProgress(null)
      setFiles([])
      if (inputRef.current) inputRef.current.value = ''
    },
    onError: () => {
      setUploadMessage('Erro ao enviar arquivo.')
      setIsError(true)
      setUploadProgress(null)
    },
  })

  const itemsQuery = useQuery({
    queryKey: ['inflation-items', ncmFilter, descriptionFilter, periodFilter],
    queryFn: () => getInflationItems({
      ncm: ncmFilter || undefined,
      description: descriptionFilter || undefined,
      period: periodFilter || undefined,
    }),
  })

  const comparisonQuery = useQuery({
    queryKey: ['inflation-comparison', ncmFilter, descriptionFilter, fromPeriod, toPeriod],
    queryFn: () => getInflationComparison({
      ncm: ncmFilter || undefined,
      description: descriptionFilter || undefined,
      from: fromPeriod,
      to: toPeriod,
    }),
    enabled: canShowChart,
  })

  const items: MarketItemDTO[] = itemsQuery.data ?? []

  const handleUpload = (e: FormEvent) => {
    e.preventDefault()
    if (files.length === 0) return
    setUploadMessage(null)
    uploadMutation.mutate(files)
  }

  const chartTitle = ncmFilter && descriptionFilter
    ? `Evolução de preço — NCM ${ncmFilter} · ${descriptionFilter}`
    : ncmFilter
      ? `Evolução de preço — NCM ${ncmFilter}${comparisonQuery.data?.description ? ` · ${comparisonQuery.data.description}` : ''}`
      : `Evolução de preço — ${descriptionFilter}`

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

            {uploadMutation.isPending && uploadProgress && uploadProgress.total > 1 && (
              <p role="status" aria-live="polite" aria-atomic="true" style={{ margin: 0, fontSize: '0.875rem', color: 'var(--color-text-muted)' }}>
                Importando {uploadProgress.current} de {uploadProgress.total}…
              </p>
            )}
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
            Descrição
            <input
              style={{ ...inputStyle, width: 200 }}
              value={descriptionFilter}
              onChange={(e) => setDescriptionFilter(e.target.value)}
              placeholder="Ex: MILHO PIPOCA"
              aria-label="Descrição"
            />
          </label>

          <label style={labelStyle}>
            Período
            <input
              type="month"
              className="month-input"
              style={{ ...inputStyle, width: 160 }}
              value={periodFilter}
              onChange={(e) => setPeriodFilter(e.target.value)}
              aria-label="Período"
            />
          </label>

          <label style={labelStyle}>
            De
            <input
              type="month"
              className="month-input"
              style={{ ...inputStyle, width: 160 }}
              value={fromPeriod}
              onChange={(e) => setFromPeriod(e.target.value)}
              aria-label="De"
            />
          </label>

          <label style={labelStyle}>
            Até
            <input
              type="month"
              className="month-input"
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
          <h2 style={{ margin: '0 0 1rem', fontSize: '1rem' }}>{chartTitle}</h2>
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
