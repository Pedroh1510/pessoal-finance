import api from './api'

const UPLOAD_MAX_RETRIES = 2
const UPLOAD_RETRY_BASE_DELAY_MS = 1000

function isAxios429(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'response' in error &&
    (error as { response: { status: number } }).response?.status === 429
  )
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export interface MarketItemDTO {
  id: string
  purchaseId: string
  period: string
  emitente: string
  chave: string
  productCode: string
  ncm: string
  description: string
  quantity: number
  unitPrice: number
  totalPrice: number
}

export interface PricePointDTO {
  period: string
  unitPrice: number
  emitente: string
}

export interface NcmComparisonDTO {
  ncm: string
  description: string
  prices: PricePointDTO[]
}

export interface InflationUploadResult {
  purchasesCreated: number
  purchasesSkipped: number
  itemsImported: number
}

export async function uploadInflation(file: File): Promise<InflationUploadResult> {
  const formData = new FormData()
  formData.append('file', file)

  let lastError: unknown
  for (let attempt = 0; attempt <= UPLOAD_MAX_RETRIES; attempt++) {
    try {
      const { data } = await api.post<InflationUploadResult>('/inflation/uploads', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      return data
    } catch (error: unknown) {
      lastError = error
      if (!isAxios429(error) || attempt === UPLOAD_MAX_RETRIES) throw error
      await sleep(UPLOAD_RETRY_BASE_DELAY_MS * Math.pow(2, attempt))
    }
  }
  throw lastError
}

export async function getInflationItems(params: {
  ncm?: string
  period?: string
}): Promise<MarketItemDTO[]> {
  const { data } = await api.get<MarketItemDTO[]>('/inflation/items', { params })
  return data
}

export async function getInflationComparison(params: {
  ncm: string
  from: string
  to: string
}): Promise<NcmComparisonDTO> {
  const { data } = await api.get<NcmComparisonDTO>('/inflation/comparison', { params })
  return data
}
