import api from './api'

export interface CategoryDTO {
  id: string
  name: string
  color: string
  isSystem: boolean
}

export async function getCategories(): Promise<CategoryDTO[]> {
  const { data } = await api.get<CategoryDTO[]>('/categories')
  return data
}

export async function createCategory(name: string, color: string): Promise<CategoryDTO> {
  const { data } = await api.post<CategoryDTO>('/categories', { name, color })
  return data
}

export async function updateCategory(
  id: string,
  name: string,
  color: string,
): Promise<CategoryDTO> {
  const { data } = await api.put<CategoryDTO>(`/categories/${id}`, { name, color })
  return data
}

export async function deleteCategory(id: string): Promise<void> {
  await api.delete(`/categories/${id}`)
}
