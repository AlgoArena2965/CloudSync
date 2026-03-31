import axios, { AxiosError } from 'axios'
import { useAuthStore } from '../stores/authStore'
import { toast } from 'sonner'

const BASE_URL = '/api'

export const api = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  },
})

// Request interceptor - add auth token
api.interceptors.request.use(
  (config) => {
    const token = useAuthStore.getState().accessToken
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor - handle auth errors
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as any

    if (error.response?.status === 401) {
      const store = useAuthStore.getState()

      // Try to refresh token
      if (store.refreshToken && !originalRequest._retry) {
        originalRequest._retry = true
        try {
          const response = await axios.post(`${BASE_URL}/auth/refresh`, {
            refreshToken: store.refreshToken,
          })
          const { accessToken, refreshToken } = response.data.data
          store.setTokens(accessToken, refreshToken)
          originalRequest.headers.Authorization = `Bearer ${accessToken}`
          return api(originalRequest)
        } catch (refreshError) {
          store.logout()
          window.location.href = '/login'
          toast.error('Session expired. Please login again.')
          return Promise.reject(refreshError)
        }
      } else {
        store.logout()
        window.location.href = '/login'
      }
    }

    // Handle specific error codes
    if (error.response?.status === 503) {
      toast.error('Service temporarily unavailable. Please try again later.')
    } else if (error.response?.status === 507) {
      toast.error('Storage quota exceeded.')
    } else if (error.response?.status === 429) {
      toast.error('Too many requests. Please wait a moment.')
    } else if (error.response?.status === 403) {
      toast.error('You do not have permission to perform this action.')
    } else if (error.response?.status === 404) {
      toast.error('Resource not found.')
    }

    return Promise.reject(error)
  }
)

// Auth
export const authApi = {
  login: (email: string, password: string) =>
    api.post('/auth/login', { email, password }),

  register: (data: { email: string; password: string; firstName?: string; lastName?: string; organizationId?: number; organizationName?: string }) =>
    api.post('/auth/register', data),

  refresh: (refreshToken: string) =>
    api.post('/auth/refresh', { refreshToken }),

  logout: () => api.post('/auth/logout'),

  me: () => api.get('/auth/me'),

  changePassword: (currentPassword: string, newPassword: string) =>
    api.post('/auth/change-password', { currentPassword, newPassword }),
}

// Files
export const fileApi = {
  list: (folderId?: number, page = 0, size = 50, sortBy = 'createdAt', sortOrder = 'DESC') => {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
      sortBy,
      sortOrder,
    })
    if (folderId !== undefined) params.set('folderId', String(folderId))
    return api.get(`/files?${params}`)
  },

  get: (fileId: number) => api.get(`/files/${fileId}`),

  search: (query: string, page = 0, size = 20) =>
    api.get(`/files/search?query=${encodeURIComponent(query)}&page=${page}&size=${size}`),

  getDashboard: () => api.get('/files/dashboard'),

  download: (fileId: number) =>
    api.get(`/files/${fileId}/download`, { responseType: 'blob' }),

  getDownloadUrl: (fileId: number) =>
    api.get(`/files/${fileId}/url`),

  rename: (fileId: number, newName: string) =>
    api.patch(`/files/${fileId}?newName=${encodeURIComponent(newName)}`),

  softDelete: (fileId: number) =>
    api.delete(`/files/${fileId}`),

  restore: (fileId: number) =>
    api.post(`/files/${fileId}/restore`),

  permanentDelete: (fileId: number) =>
    api.delete(`/files/${fileId}/permanent`),

  getTrash: (page = 0, size = 20) =>
    api.get(`/files/trash?page=${page}&size=${size}`),
}

// Upload
export const uploadApi = {
  init: (data: { fileName: string; fileSize: number; mimeType?: string; folderId?: number; organizationId?: number; resumable?: boolean; fileHash?: string }) =>
    api.post('/upload/init', data),

  uploadChunk: (sessionToken: string, partNumber: number, file: Blob) => {
    const formData = new FormData()
    formData.append('sessionToken', sessionToken)
    formData.append('partNumber', String(partNumber))
    formData.append('file', file)
    return api.post('/upload/chunk', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000,
    })
  },

  uploadChunkBinary: (sessionToken: string, partNumber: number, data: ArrayBuffer) =>
    api.post('/upload/chunk/binary', data, {
      headers: {
        'Content-Type': 'application/octet-stream',
        'X-Session-Token': sessionToken,
        'X-Part-Number': String(partNumber),
      },
      timeout: 120000,
    }),

  complete: (sessionToken: string, fileHash?: string) =>
    api.post('/upload/complete', { sessionToken, fileHash }),

  getStatus: (sessionToken: string) =>
    api.get(`/upload/status/${sessionToken}`),

  getPending: (sessionToken: string) =>
    api.get(`/upload/pending/${sessionToken}`),

  cancel: (sessionToken: string) =>
    api.post(`/upload/cancel/${sessionToken}`),

  direct: (file: File, folderId?: number) => {
    const formData = new FormData()
    formData.append('file', file)
    if (folderId) formData.append('folderId', String(folderId))
    return api.post('/upload/direct', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000,
    })
  },
}

// Folders
export const folderApi = {
  create: (data: { name: string; parentFolderId?: number; organizationId?: number }) =>
    api.post('/folders', data),

  get: (folderId: number) => api.get(`/folders/${folderId}`),

  getRoot: () => api.get('/folders/root'),

  getSubFolders: (parentId: number) =>
    api.get(`/folders/${parentId}/children`),

  getFilesInFolder: (folderId: number) =>
    api.get(`/folders/${folderId}/files`),

  getRootFiles: () => api.get('/folders/files/root'),

  rename: (folderId: number, newName: string) =>
    api.patch(`/folders/${folderId}/rename?newName=${encodeURIComponent(newName)}`),

  delete: (folderId: number) =>
    api.delete(`/folders/${folderId}`),
}

// Shares
export const shareApi = {
  create: (data: { fileId: number; shareType?: string; maxDownloads?: number; expiresAt?: string; allowPreview?: boolean; allowEdit?: boolean }) =>
    api.post('/shares', data),

  getByFile: (fileId: number) =>
    api.get(`/shares/file/${fileId}`),

  getMyShares: () => api.get('/shares/me'),

  deactivate: (shareId: number) =>
    api.delete(`/shares/${shareId}`),

  getSharedFile: (shareToken: string) =>
    api.get(`/public/share/${shareToken}`),

  downloadShared: (shareToken: string) =>
    api.get(`/public/share/${shareToken}/download`, { responseType: 'blob' }),

  getSharedUrl: (shareToken: string) =>
    api.get(`/public/share/${shareToken}/url`),
}

// Organizations
export const orgApi = {
  get: (orgId: number) => api.get(`/organizations/${orgId}`),

  getActive: () => api.get('/organizations/active'),
}

// Health
export const healthApi = {
  getStatus: () => api.get('/health/status'),
}
