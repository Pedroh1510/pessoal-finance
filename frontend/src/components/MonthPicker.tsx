interface MonthPickerProps {
  value: string
  onChange: (value: string) => void
}

const MONTHS = [
  'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
  'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro',
]

function parseYearMonth(value: string): { year: number; month: number } {
  const [year, month] = value.split('-').map(Number)
  return { year, month }
}

function formatYearMonth(year: number, month: number): string {
  return `${year}-${String(month).padStart(2, '0')}`
}

export default function MonthPicker({ value, onChange }: MonthPickerProps) {
  const { year, month } = parseYearMonth(value)

  function navigate(delta: number) {
    let newMonth = month + delta
    let newYear = year
    if (newMonth > 12) { newMonth = 1; newYear++ }
    if (newMonth < 1) { newMonth = 12; newYear-- }
    onChange(formatYearMonth(newYear, newMonth))
  }

  return (
    <div style={containerStyle}>
      <button
        type="button"
        onClick={() => navigate(-1)}
        style={arrowStyle}
        aria-label="Mês anterior"
      >
        ‹
      </button>
      <span style={labelStyle}>
        {MONTHS[month - 1]} {year}
      </span>
      <button
        type="button"
        onClick={() => navigate(1)}
        style={arrowStyle}
        aria-label="Próximo mês"
      >
        ›
      </button>
    </div>
  )
}

const containerStyle: React.CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: '0.25rem',
  border: '1px solid var(--color-border-input)',
  borderRadius: '4px',
  padding: '0.25rem 0.4rem',
  background: 'var(--color-surface)',
  height: '32px',
  boxSizing: 'border-box',
}

const arrowStyle: React.CSSProperties = {
  background: 'none',
  border: 'none',
  cursor: 'pointer',
  fontSize: '1.1rem',
  lineHeight: 1,
  padding: '0 0.2rem',
  color: 'var(--color-text)',
  display: 'flex',
  alignItems: 'center',
}

const labelStyle: React.CSSProperties = {
  fontSize: '0.875rem',
  color: 'var(--color-text)',
  minWidth: '130px',
  textAlign: 'center',
  userSelect: 'none',
}
