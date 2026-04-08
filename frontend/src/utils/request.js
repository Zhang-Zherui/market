import axios from 'axios'
import router from '../router'

const request = axios.create({
  timeout: 10000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token')
  if (token) {
    config.headers.Authorization = token
  }
  return config
})

request.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    const { response } = error
    if (response && response.status === 401) {
      const refreshToken = localStorage.getItem('refresh_token')
      if (refreshToken) {
        try {
          const res = await axios.post('/user/refresh', { refreshToken })
          if (res.data.success) {
            localStorage.setItem('access_token', res.data.data.access_token)
            localStorage.setItem('refresh_token', res.data.data.refresh_token)
            error.config.headers.Authorization = res.data.data.access_token
            return request(error.config)
          }
        } catch {
          // refresh failed
        }
      }
      localStorage.removeItem('access_token')
      localStorage.removeItem('refresh_token')
      router.push('/login')
    }
    return Promise.reject(error)
  }
)

export default request
