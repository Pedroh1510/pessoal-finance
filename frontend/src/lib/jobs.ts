import api from './api'

export type JobStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface JobResponse {
  id: string
  type: string
  status: JobStatus
  result: Record<string, number> | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export async function getJob(id: string): Promise<JobResponse> {
  const { data } = await api.get<JobResponse>(`/jobs/${id}`)
  return data
}

export function isTerminal(status: JobStatus): boolean {
  return status === 'COMPLETED' || status === 'FAILED'
}
