import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const url = error.config?.url as string | undefined
    const isAuthEndpoint = url?.startsWith('/auth') ?? false
    if (error.response?.status === 401 && !isAuthEndpoint && typeof window !== 'undefined') {
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default api
