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

export default function UploadsPage() {
  const [file, setFile] = useState<File | null>(null)
  const [history, setHistory] = useState<UploadHistoryEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const { register, handleSubmit, reset } = useForm<UploadFormValues>({
    defaultValues: { bank: 'NUBANK' },
  })

  const onSubmit = async (values: UploadFormValues) => {
    if (!file) {
      setError('Selecione um arquivo PDF.')
      return
    }

    setLoading(true)
    setError(null)

    try {
      const result = await uploadStatement(file, values.bank)
      setHistory((prev) => [
        { filename: file.name, bank: values.bank, result, uploadedAt: new Date() },
        ...prev,
      ])
      setFile(null)
      reset()
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message)
      } else {
        setError('Erro ao realizar upload. Tente novamente.')
      }
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
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                style={{ fontSize: '0.9rem' }}
                aria-label="Selecionar arquivo PDF"
              />
              {file && (
                <span style={{ fontSize: '0.8rem', color: '#555' }}>
                  Selecionado: {file.name}
                </span>
              )}
            </label>

            {error && <p style={{ color: '#d61f69', margin: 0 }}>{error}</p>}

            <button
              type="submit"
              disabled={loading || !file}
              style={{
                padding: '0.6rem 1.5rem',
                background: '#1a56db',
                color: '#fff',
                border: 'none',
                borderRadius: '4px',
                cursor: loading || !file ? 'not-allowed' : 'pointer',
                fontSize: '0.9rem',
                opacity: loading || !file ? 0.65 : 1,
                alignSelf: 'flex-start',
              }}
            >
              {loading ? 'Enviando...' : 'Enviar extrato'}
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
                  <span style={{ fontSize: '0.8rem', color: '#888' }}>
                    {entry.uploadedAt.toLocaleString('pt-BR')}
                  </span>
                </div>
                <div style={{ fontSize: '0.85rem', color: '#555' }}>
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
                    <strong style={{ color: '#1a56db' }}>{entry.result.total}</strong> transações importadas
                  </span>
                  <span>
                    <strong style={{ color: '#5850ec' }}>{entry.result.internalTransfers}</strong> transferências internas
                  </span>
                  <span>
                    <strong style={{ color: '#e3a008' }}>{entry.result.uncategorized}</strong> sem categoria
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
  background: '#fff',
  border: '1px solid #e5e7eb',
  borderRadius: '8px',
  padding: '1.5rem',
}

const labelStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '0.3rem',
  fontSize: '0.85rem',
  color: '#555',
  fontWeight: 500,
}

const inputStyle: React.CSSProperties = {
  padding: '0.45rem 0.6rem',
  border: '1px solid #d1d5db',
  borderRadius: '4px',
  fontSize: '0.9rem',
}
