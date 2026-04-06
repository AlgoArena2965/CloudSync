import { useState, useCallback } from 'react'
import { useDropzone } from 'react-dropzone'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Upload, X, CheckCircle2, AlertCircle, Loader2, FileText,
  Cloud, ArrowUp, Zap, Shield, Pause, Play
} from 'lucide-react'
import { toast } from 'sonner'
import { uploadApi } from '../services/api'
import { useFileStore } from '../stores/fileStore'

interface UploadItem {
  id: string
  file: File
  progress: number
  status: 'pending' | 'uploading' | 'completed' | 'error'
  error?: string
  sessionToken?: string
  totalChunks?: number
  uploadedChunks?: number
}

export default function UploadPage() {
  const { addUpload, removeUpload, updateUpload } = useFileStore()
  const [uploads, setUploads] = useState<UploadItem[]>([])
  const [isDragging, setIsDragging] = useState(false)

  const uploadFile = async (item: UploadItem) => {
    try {
      // Initialize upload
      const initRes = await uploadApi.init({
        fileName: item.file.name,
        fileSize: item.file.size,
        resumable: item.file.size > 10 * 1024 * 1024, // >10MB uses resumable
      })

      const { sessionToken, totalChunks, chunkSizeBytes } = initRes.data.data
      item.sessionToken = sessionToken
      item.totalChunks = totalChunks
      item.status = 'uploading'
      setUploads(prev => [...prev])

      if (totalChunks > 1) {
        // Resumable upload - chunk by chunk
        const chunkSize = chunkSizeBytes
        const totalChunksNum = totalChunks

        for (let i = 1; i <= totalChunksNum; i++) {
          const start = (i - 1) * chunkSize
          const end = Math.min(start + chunkSize, item.file.size)
          const chunk = item.file.slice(start, end)

          try {
            await uploadApi.uploadChunk(sessionToken, i, chunk)
            item.uploadedChunks = i
            item.progress = Math.round((i / totalChunksNum) * 100)
            setUploads(prev => [...prev])
          } catch (chunkErr: any) {
            if (chunkErr.response?.status === 503) {
              // Circuit breaker - wait and retry
              await new Promise(r => setTimeout(r, 5000))
              i-- // retry same chunk
              continue
            }
            throw chunkErr
          }
        }

        // Complete upload
        await uploadApi.complete(sessionToken)
      } else {
        // Direct upload for small files
        await uploadApi.direct(item.file)
      }

      item.status = 'completed'
      item.progress = 100
      setUploads(prev => [...prev])
      toast.success(`${item.file.name} uploaded successfully!`)
    } catch (err: any) {
      item.status = 'error'
      item.error = err.response?.data?.message || err.message || 'Upload failed'
      setUploads(prev => [...prev])
      toast.error(`${item.file.name}: ${item.error}`)
    }
  }

  const onDrop = useCallback(async (acceptedFiles: File[]) => {
    setIsDragging(false)
    const newItems: UploadItem[] = acceptedFiles.map(file => ({
      id: `${Date.now()}-${Math.random()}`,
      file,
      progress: 0,
      status: 'pending',
    }))

    setUploads(prev => [...prev, ...newItems])

    // Start uploads concurrently (max 3 at a time)
    const queue = [...newItems]
    const concurrent = 3

    const processQueue = async () => {
      while (queue.length > 0) {
        const batch = queue.splice(0, concurrent)
        await Promise.all(batch.map(item => uploadFile(item)))
      }
    }

    processQueue()
  }, [])

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    onDragEnter: () => setIsDragging(true),
    onDragLeave: () => setIsDragging(false),
  })

  const removeUploadItem = (id: string) => {
    setUploads(prev => prev.filter(u => u.id !== id))
  }

  const completedCount = uploads.filter(u => u.status === 'completed').length
  const totalCount = uploads.length

  return (
    <div className="space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-white">Upload Files</h1>
        <p className="text-slate-400 mt-1">
          Drag and drop files or click to browse
        </p>
      </div>

      {/* Drop zone */}
      <div
        {...getRootProps()}
        className={`dropzone transition-all duration-300 ${isDragActive ? 'border-brand-500 bg-brand-500/5' : ''}`}
      >
        <input {...getInputProps()} />
        <div className="w-20 h-20 rounded-3xl bg-gradient-to-br from-brand-500/20 to-brand-600/20 flex items-center justify-center mb-4">
          <Upload className="w-10 h-10 text-brand-400" />
        </div>
        <h3 className="text-xl font-semibold text-white">
          {isDragActive ? 'Drop files here' : 'Drag & drop files here'}
        </h3>
        <p className="text-slate-400">or</p>
        <button className="btn-primary mt-2">
          Browse Files
        </button>
        <p className="text-xs text-slate-500 mt-4">Supports files up to 10GB</p>
      </div>

      {/* Features */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {[
          { icon: Zap, title: 'Resumable Uploads', desc: 'Large files resume automatically if interrupted' },
          { icon: Shield, title: 'Secure Transfer', desc: 'Files are encrypted during upload' },
          { icon: Cloud, title: 'S3 Storage', desc: 'Stored redundantly across multiple locations' },
        ].map(({ icon: Icon, title, desc }) => (
          <div key={title} className="p-5 rounded-2xl bg-white/[0.03] border border-white/5">
            <Icon className="w-6 h-6 text-brand-400 mb-3" />
            <h4 className="text-sm font-semibold text-white mb-1">{title}</h4>
            <p className="text-xs text-slate-400">{desc}</p>
          </div>
        ))}
      </div>

      {/* Upload progress */}
      {uploads.length > 0 && (
        <div>
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-xl font-semibold text-white">
              Uploads ({completedCount}/{totalCount})
            </h2>
          </div>

          <div className="space-y-3">
            <AnimatePresence>
              {uploads.map((item, i) => (
                <motion.div
                  key={item.id}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, x: -20 }}
                  transition={{ delay: i * 0.05 }}
                  className="flex items-center gap-4 p-4 rounded-2xl bg-white/[0.03] border border-white/5"
                >
                  <div className={`w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0 ${
                    item.status === 'completed' ? 'bg-emerald-500/20' :
                    item.status === 'error' ? 'bg-red-500/20' :
                    'bg-brand-500/20'
                  }`}>
                    {item.status === 'completed' ? (
                      <CheckCircle2 className="w-6 h-6 text-emerald-400" />
                    ) : item.status === 'error' ? (
                      <AlertCircle className="w-6 h-6 text-red-400" />
                    ) : item.status === 'uploading' ? (
                      <Loader2 className="w-6 h-6 text-brand-400 animate-spin" />
                    ) : (
                      <FileText className="w-6 h-6 text-slate-400" />
                    )}
                  </div>

                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-white truncate">{item.file.name}</p>
                    <div className="flex items-center gap-3 mt-1">
                      <span className="text-xs text-slate-500">{formatBytes(item.file.size)}</span>
                      {item.status === 'uploading' && (
                        <span className="text-xs text-brand-400">{item.progress}%</span>
                      )}
                      {item.status === 'error' && (
                        <span className="text-xs text-red-400">{item.error}</span>
                      )}
                      {item.status === 'completed' && (
                        <span className="text-xs text-emerald-400">Completed</span>
                      )}
                    </div>
                    {item.status === 'uploading' && (
                      <div className="mt-2 h-1.5 rounded-full bg-white/10 overflow-hidden">
                        <motion.div
                          className="h-full rounded-full bg-gradient-to-r from-brand-500 to-brand-400"
                          initial={{ width: 0 }}
                          animate={{ width: `${item.progress}%` }}
                          transition={{ duration: 0.3 }}
                        />
                      </div>
                    )}
                  </div>

                  {item.status !== 'uploading' && (
                    <button
                      onClick={() => removeUploadItem(item.id)}
                      className="p-2 rounded-lg hover:bg-white/5 text-slate-500 transition-colors"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  )}
                </motion.div>
              ))}
            </AnimatePresence>
          </div>
        </div>
      )}
    </div>
  )
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`
}
