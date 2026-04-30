import { useState, useEffect, useRef } from 'react'
import { useForm } from 'react-hook-form'
import { uploadStatement, BankName } from '../lib/finance'
import { getJob, JobResponse, isTerminal } from '../lib/jobs'

interface UploadFormValues {
  bank: BankName
}

interface UploadEntry {
  filename: string
  bank: BankName
  jobId: string
  job: JobResponse | null
}

export default function UploadsPage() {
  const [files, setFiles] = useState<File[]>([])
  const [entries, setEntries] = useState<UploadEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [failures, setFailures] = useState<{ filename: string; error: string }[]>([])
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const { register, handleSubmit, reset } = useForm<UploadFormValues>({
    defaultValues: { bank: 'NUBANK' },
  })

  useEffect(() => {
    const pending = entries.filter((e) => e.job === null || !isTerminal(e.job.status))
    if (pending.length === 0) {
      if (pollingRef.current) clearInterval(pollingRef.current)
      return
    }
    pollingRef.current = setInterval(async () => {
      for (const entry of pending) {
        try {
          const job = await getJob(entry.jobId)
          setEntries((prev) =>
            prev.map((e) => (e.jobId === entry.jobId ? { ...e, job } : e))
          )
        } catch { /* ignore polling errors */ }
      }
    }, 2000)
    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current)
    }
  }, [entries])

  const onSubmit = async (values: UploadFormValues) => {
    if (files.length === 0) return
    setLoading(true)
    setFailures([])
    const newFailures: { filename: string; error: string }[] = []
    for (const file of files) {
      try {
        const { jobId } = await uploadStatement(file, values.bank)
        setEntries((prev) => [
          { filename: file.name, bank: values.bank, jobId, job: null },
          ...prev,
        ])
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : 'Erro ao realizar upload.'
        newFailures.push({ filename: file.name, error: message })
      }
    }
    setFailures(newFailures)
    setFiles([])
    reset()
    setLoading(false)
  }

  return (
    <div style={{ maxWidth: 640 }}>
      <h1 style={{ marginBottom: '1.5rem' }}>Upload de Extrato</h1>

      <div style={cardStyle}>
        <form onSubmit={handleSubmit(onSubmit)}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <label style={labelStyle}>
              Banco
              <select
                {...register('bank', { required: true })}
                style={inputStyle}
                aria-label="Selecionar banco"
              >
                <option value="NUBANK">Nubank</option>
                <option value="NEON">Neon</option>
                <option value="INTER">Inter</option>
              </select>
            </label>
            <label style={labelStyle}>
              Arquivo PDF
              <input
                type="file"
                accept="application/pdf"
                multiple
                onChange={(e) => setFiles(Array.from(e.target.files ?? []))}
                aria-label="Selecionar arquivos PDF"
              />
              {files.length > 0 && (
                <span style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
                  {files.length} arquivo(s) selecionado(s)
                </span>
              )}
            </label>
            {failures.length > 0 && (
              <ul
                role="alert"
                style={{
                  margin: 0,
                  paddingLeft: '1.2rem',
                  color: 'var(--color-danger)',
                  fontSize: '0.875rem',
                }}
              >
                {failures.map((f) => (
                  <li key={f.filename}>
                    <strong>{f.filename}</strong>: {f.error}
                  </li>
                ))}
              </ul>
            )}
            <button
              type="submit"
              disabled={loading || files.length === 0}
              style={{
                padding: '0.6rem 1.5rem',
                background: 'var(--color-accent)',
                color: '#fff',
                border: 'none',
                borderRadius: '4px',
                cursor: loading || files.length === 0 ? 'not-allowed' : 'pointer',
                fontSize: '0.9rem',
                opacity: loading || files.length === 0 ? 0.65 : 1,
                alignSelf: 'flex-start',
              }}
            >
              {loading ? 'Enviando...' : 'Enviar extratos'}
            </button>
          </div>
        </form>
      </div>

      {entries.length > 0 && (
        <div style={{ marginTop: '2rem' }}>
          <h2 style={{ marginBottom: '1rem', fontSize: '1.1rem' }}>Uploads em andamento</h2>
          <ul
            style={{
              listStyle: 'none',
              padding: 0,
              margin: 0,
              display: 'flex',
              flexDirection: 'column',
              gap: '0.75rem',
            }}
          >
            {entries.map((entry) => (
              <li key={entry.jobId} style={{ ...cardStyle, padding: '1rem' }}>
                <div
                  style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}
                >
                  <strong style={{ fontSize: '0.9rem' }}>{entry.filename}</strong>
                  <span style={{ fontSize: '0.8rem', color: statusColor(entry.job?.status) }}>
                    {entry.job?.status ?? 'Enviando...'}
                  </span>
                </div>
                {entry.job?.status === 'COMPLETED' && entry.job.result && (
                  <div
                    style={{ display: 'flex', gap: '1.5rem', fontSize: '0.875rem', marginTop: '0.5rem' }}
                  >
                    <span>
                      <strong style={{ color: 'var(--color-accent)' }}>
                        {entry.job.result.total}
                      </strong>{' '}
                      transações
                    </span>
                    <span>
                      <strong style={{ color: 'var(--color-purple)' }}>
                        {entry.job.result.internalTransfers}
                      </strong>{' '}
                      internas
                    </span>
                    <span>
                      <strong style={{ color: 'var(--color-warning)' }}>
                        {entry.job.result.uncategorized}
                      </strong>{' '}
                      sem categoria
                    </span>
                  </div>
                )}
                {entry.job?.status === 'FAILED' && (
                  <p
                    style={{ margin: '0.5rem 0 0', color: 'var(--color-danger)', fontSize: '0.875rem' }}
                  >
                    {entry.job.errorMessage ?? 'Erro desconhecido'}
                  </p>
                )}
                {(!entry.job || !isTerminal(entry.job.status)) && (
                  <p
                    style={{ margin: '0.5rem 0 0', fontSize: '0.8rem', color: 'var(--color-text-muted)' }}
                  >
                    Processando… (atualiza automaticamente)
                  </p>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function statusColor(status?: string): string {
  if (status === 'COMPLETED') return 'var(--color-success)'
  if (status === 'FAILED') return 'var(--color-danger)'
  return 'var(--color-text-muted)'
}

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
