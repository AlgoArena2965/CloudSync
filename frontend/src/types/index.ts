export interface ApiResponse<T = any> {
  success: boolean
  message?: string
  data: T
  errors?: any
  timestamp?: string
  path?: string
}

export interface User {
  id: number
  email: string
  firstName?: string
  lastName?: string
  fullName?: string
  role: string
  isEnabled: boolean
  isEmailVerified: boolean
  storageUsedBytes: number
  organizationId?: number
  organizationName?: string
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: User
}

export interface FileEntity {
  id: number
  name: string
  originalName?: string
  mimeType?: string
  fileSizeBytes: number
  storageSizeBytes?: number
  status: string
  folderId?: number
  folderName?: string
  folderPath?: string
  ownerId: number
  ownerName?: string
  organizationId?: number
  organizationName?: string
  fileHash?: string
  contentType?: string
  extension?: string
  isDeleted?: boolean
  isShared?: boolean
  downloadCount?: number
  lastAccessedAt?: string
  version?: number
  nodePartition?: string
  downloadUrl?: string
  previewUrl?: string
  s3Key?: string
  createdAt: string
  updatedAt: string
}

export interface Folder {
  id: number
  name: string
  parentFolderId?: number
  parentFolderPath?: string
  ownerId: number
  ownerName?: string
  organizationId?: number
  organizationName?: string
  folderPath: string
  isShared?: boolean
  totalSizeBytes: number
  fileCount: number
  subFolderCount: number
  files?: FileEntity[]
  subFolders?: Folder[]
  createdAt: string
  updatedAt: string
}

export interface FileShare {
  id: number
  fileId: number
  fileName: string
  shareToken: string
  shareType: string
  sharedByUserId: number
  sharedByUserName?: string
  sharedWithUserId?: number
  sharedWithUserName?: string
  shareUrl?: string
  maxDownloads?: number
  currentDownloads?: number
  expiresAt?: string
  allowPreview?: boolean
  allowEdit?: boolean
  viewCount?: number
  isActive?: boolean
  isExpired?: boolean
  createdAt: string
}

export interface Dashboard {
  totalFiles: number
  totalFolders: number
  totalStorageUsedBytes: number
  totalStorageQuotaBytes: number
  usagePercentage: number
  totalShares?: number
  activeShares?: number
  totalDownloads?: number
  recentFileCount: number
  recentFiles: FileEntity[]
}

export interface UploadInitResponse {
  sessionToken: string
  uploadId: string
  fileName: string
  fileSizeBytes: number
  totalChunks: number
  chunkSizeBytes: number
  resumable: boolean
  expiresAt: string
  s3Key: string
}

export interface ChunkUploadResponse {
  sessionToken: string
  partNumber?: number
  etag?: string
  uploadedChunks: number
  totalChunks: number
  progressPercentage: number
  isComplete: boolean
  lastChunkAt: string
}

export interface UploadCompleteResponse {
  sessionToken: string
  fileId: number
  fileName: string
  fileSizeBytes: number
  s3Key: string
  completed: boolean
  completedAt: string
  downloadUrl: string
  message: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  hasNext: boolean
  hasPrevious: boolean
}

export interface Organization {
  id: number
  name: string
  slug?: string
  description?: string
  storageQuotaBytes: number
  storageUsedBytes: number
  usagePercentage: number
  status: string
  maxUsers: number
  currentUserCount: number
  ownerId?: number
  ownerName?: string
  createdAt: string
}
