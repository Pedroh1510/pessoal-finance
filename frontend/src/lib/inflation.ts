import api from './api'

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
  const { data } = await api.post<InflationUploadResult>('/inflation/uploads', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
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
