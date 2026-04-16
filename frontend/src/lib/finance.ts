import api from './api'

export type BankName = 'NUBANK' | 'NEON' | 'INTER'
export type TransactionType = 'INCOME' | 'EXPENSE' | 'INTERNAL_TRANSFER'

export interface TransactionDTO {
  transactionId: string
  date: string
  amount: number
  recipient: string | null
  description: string | null
  categoryId: string | null
  categoryName: string | null
  type: TransactionType
  bankName: BankName
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface UploadResult {
  total: number
  internalTransfers: number
  uncategorized: number
}

export interface RecipientRuleDTO {
  id: string
  recipientPattern: string
  categoryId: string
  categoryName: string
}

export interface InternalAccountRuleDTO {
  id: string
  identifier: string
  type: string
}

export async function getTransactions(params: {
  month?: string
  categoryId?: string
  bank?: BankName
  type?: TransactionType
  page?: number
  size?: number
}): Promise<Page<TransactionDTO>> {
  const { data } = await api.get<Page<TransactionDTO>>('/finance/transactions', { params })
  return data
}

export async function getTransaction(id: string): Promise<TransactionDTO> {
  const { data } = await api.get<TransactionDTO>(`/finance/transactions/${id}`)
  return data
}

export async function categorizeTransaction(id: string, categoryId: string): Promise<void> {
  await api.put(`/finance/transactions/${id}/category`, { categoryId })
}

export async function uploadStatement(file: File, bankName: BankName): Promise<UploadResult> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('bankName', bankName)
  const { data } = await api.post<UploadResult>('/finance/uploads', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
}

export async function getRecipientRules(): Promise<RecipientRuleDTO[]> {
  const { data } = await api.get<RecipientRuleDTO[]>('/finance/rules/recipient')
  return data
}

export async function createRecipientRule(
  recipientPattern: string,
  categoryId: string,
): Promise<void> {
  await api.post('/finance/rules/recipient', { recipientPattern, categoryId })
}

export async function deleteRecipientRule(id: string): Promise<void> {
  await api.delete(`/finance/rules/recipient/${id}`)
}

export async function getInternalAccountRules(): Promise<InternalAccountRuleDTO[]> {
  const { data } = await api.get<InternalAccountRuleDTO[]>('/finance/rules/internal-accounts')
  return data
}

export async function createInternalAccountRule(
  identifier: string,
  type: string,
): Promise<void> {
  await api.post('/finance/rules/internal-accounts', { identifier, type })
}

export async function deleteInternalAccountRule(id: string): Promise<void> {
  await api.delete(`/finance/rules/internal-accounts/${id}`)
}
