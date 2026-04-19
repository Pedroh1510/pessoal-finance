import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { uploadStatement, BankName, UploadResult } from '../lib/finance'

interface UploadFormValues {
  bank: BankName
}

interface UploadHistoryEntry {
  filename: string
  bank: BankName
  result: UploadResult
  uploadedAt: Date
}

interface UploadFailure {
  filename: string
  error: string
}

export default function UploadsPage() {
  const [files, setFiles] = useState<File[]>([])
  const [history, setHistory] = useState<UploadHistoryEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [failures, setFailures] = useState<UploadFailure[]>([])

  const { register, handleSubmit, reset } = useForm<UploadFormValues>({
    defaultValues: { bank: 'NUBANK' },
  })

  const onSubmit = async (values: UploadFormValues) => {
    if (files.length === 0) return

    setLoading(true)
    setFailures([])

    const newFailures: UploadFailure[] = []

    try {
      for (const file of files) {
        try {
          const result = await uploadStatement(file, values.bank)
          setHistory((prev) => [
            { filename: file.name, bank: values.bank, result, uploadedAt: new Date() },
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
    } finally {
      setLoading(false)
    }
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
                style={{ fontSize: '0.9rem' }}
                aria-label="Selecionar arquivos PDF"
              />
              {files.length > 0 && (
                <span style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
                  {files.length} arquivo(s) selecionado(s)
                </span>
              )}
            </label>

            {failures.length > 0 && (
              <ul role="alert" style={{ margin: 0, paddingLeft: '1.2rem', color: 'var(--color-danger)', fontSize: '0.875rem' }}>
                {failures.map((f) => (
                  <li key={f.filename}><strong>{f.filename}</strong>: {f.error}</li>
                ))}
              </ul>
            )}

            <button
              type="submit"
              disabled={loading || files.length === 0}
              style={{
                padding: '0.6rem 1.5rem',
                background: '#1a56db',
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

      {history.length > 0 && (
        <div style={{ marginTop: '2rem' }}>
          <h2 style={{ marginBottom: '1rem', fontSize: '1.1rem' }}>Histórico de uploads</h2>
          <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {history.map((entry, i) => (
              <li key={i} style={{ ...cardStyle, padding: '1rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                  <strong style={{ fontSize: '0.9rem' }}>{entry.filename}</strong>
                  <span style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
                    {entry.uploadedAt.toLocaleString('pt-BR')}
                  </span>
                </div>
                <div style={{ fontSize: '0.85rem', color: 'var(--color-text-muted)' }}>
                  Banco: <strong>{entry.bank}</strong>
                </div>
                <div
                  style={{
                    marginTop: '0.5rem',
                    display: 'flex',
                    gap: '1.5rem',
                    fontSize: '0.875rem',
                  }}
                >
                  <span>
                    <strong style={{ color: 'var(--color-accent)' }}>{entry.result.total}</strong> transações importadas
                  </span>
                  <span>
                    <strong style={{ color: 'var(--color-purple)' }}>{entry.result.internalTransfers}</strong> transferências internas
                  </span>
                  <span>
                    <strong style={{ color: 'var(--color-warning)' }}>{entry.result.uncategorized}</strong> sem categoria
                  </span>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
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
