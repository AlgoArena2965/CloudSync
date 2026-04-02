import { create } from 'zustand'
import { FileEntity, Folder, Dashboard, FileShare, ChunkUploadResponse } from '../types'

interface FileState {
  files: FileEntity[]
  folders: Folder[]
  currentFolderId: number | null
  folderPath: Folder[]
  dashboard: Dashboard | null
  sharedFiles: FileShare[]
  uploads: Map<string, ChunkUploadResponse & { fileName: string; progress: number }>
  isLoading: boolean

  setFiles: (files: FileEntity[]) => void
  setFolders: (folders: Folder[]) => void
  setCurrentFolder: (folderId: number | null, path: Folder[]) => void
  setDashboard: (dashboard: Dashboard) => void
  setSharedFiles: (shares: FileShare[]) => void
  addUpload: (token: string, upload: ChunkUploadResponse & { fileName: string }) => void
  updateUpload: (token: string, progress: number) => void
  removeUpload: (token: string) => void
  setLoading: (loading: boolean) => void
}

export const useFileStore = create<FileState>()((set, get) => ({
  files: [],
  folders: [],
  currentFolderId: null,
  folderPath: [],
  dashboard: null,
  sharedFiles: [],
  uploads: new Map(),
  isLoading: false,

  setFiles: (files) => set({ files }),
  setFolders: (folders) => set({ folders }),
  setCurrentFolder: (folderId, path) => set({ currentFolderId: folderId, folderPath: path }),
  setDashboard: (dashboard) => set({ dashboard }),
  setSharedFiles: (shares) => set({ sharedFiles: shares }),
  setLoading: (loading) => set({ isLoading: loading }),

  addUpload: (token, upload) => {
    const uploads = new Map(get().uploads)
    uploads.set(token, { ...upload, progress: 0 })
    set({ uploads })
  },

  updateUpload: (token, progress) => {
    const uploads = new Map(get().uploads)
    const existing = uploads.get(token)
    if (existing) {
      uploads.set(token, { ...existing, progress })
    }
    set({ uploads })
  },

  removeUpload: (token) => {
    const uploads = new Map(get().uploads)
    uploads.delete(token)
    set({ uploads })
  },
}))
