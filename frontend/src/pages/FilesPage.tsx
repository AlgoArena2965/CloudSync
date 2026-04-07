import { useEffect, useState, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import {
  FolderPlus, Upload, FileText, Folder as FolderIcon, Grid3X3, List,
  Search, SlidersHorizontal, MoreVertical, Download, Trash2,
  Link2, Eye, ChevronRight, Home, Loader2, RefreshCw,
  X, Edit3, ExternalLink, Check, Clock, AlertCircle
} from 'lucide-react'
import { fileApi, folderApi, shareApi } from '../services/api'
import { useFileStore } from '../stores/fileStore'
import { FileEntity, Folder as FolderType } from '../types'
import { toast } from 'sonner'
import { format } from 'date-fns'

export default function FilesPage() {
  const { folderId } = useParams()
  const navigate = useNavigate()
  const { files, folders, setFiles, setFolders, isLoading, setLoading } = useFileStore()
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid')
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedItems, setSelectedItems] = useState<Set<number>>(new Set())
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; item: FileEntity | FolderType } | null>(null)
  const [shareModal, setShareModal] = useState<FileEntity | null>(null)
  const [renameModal, setRenameModal] = useState<FileEntity | null>(null)
  const [renameValue, setRenameValue] = useState('')
  const [shareLink, setShareLink] = useState('')
  const [creatingFolder, setCreatingFolder] = useState(false)
  const [newFolderName, setNewFolderName] = useState('')

  const currentFolderId = folderId ? parseInt(folderId) : null

  const loadFiles = useCallback(async () => {
    setLoading(true)
    try {
      if (currentFolderId) {
        const [filesRes, foldersRes] = await Promise.all([
          folderApi.getFilesInFolder(currentFolderId),
          folderApi.getSubFolders(currentFolderId),
        ])
        setFiles(filesRes.data.data || [])
        setFolders(foldersRes.data.data || [])
      } else {
        const [rootFolders, rootFiles] = await Promise.all([
          folderApi.getRoot(),
          folderApi.getRootFiles(),
        ])
        setFolders(rootFolders.data.data || [])
        setFiles(rootFiles.data.data || [])
      }
    } catch (err) {
      toast.error('Failed to load files')
    } finally {
      setLoading(false)
    }
  }, [currentFolderId])

  useEffect(() => {
    loadFiles()
  }, [loadFiles])

  // Close context menu on click outside
  useEffect(() => {
    const handleClick = () => setContextMenu(null)
    window.addEventListener('click', handleClick)
    return () => window.removeEventListener('click', handleClick)
  }, [])

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!searchQuery.trim()) {
      loadFiles()
      return
    }
    setLoading(true)
    try {
      const res = await fileApi.search(searchQuery)
      setFiles(res.data.data?.content || [])
      setFolders([])
    } catch {
      toast.error('Search failed')
    } finally {
      setLoading(false)
    }
  }

  const handleCreateFolder = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newFolderName.trim()) return
    try {
      await folderApi.create({ name: newFolderName, parentFolderId: currentFolderId || undefined })
      toast.success('Folder created')
      setNewFolderName('')
      setCreatingFolder(false)
      loadFiles()
    } catch {
      toast.error('Failed to create folder')
    }
  }

  const handleDelete = async (file: FileEntity) => {
    try {
      await fileApi.softDelete(file.id)
      toast.success('Moved to trash')
      loadFiles()
    } catch {
      toast.error('Delete failed')
    }
  }

  const handleDownload = async (file: FileEntity) => {
    try {
      const res = await fileApi.download(file.id)
      const url = window.URL.createObjectURL(new Blob([res.data]))
      const a = document.createElement('a')
      a.href = url
      a.download = file.name
      document.body.appendChild(a)
      a.click()
      window.URL.revokeObjectURL(url)
      document.body.removeChild(a)
      toast.success('Download started')
    } catch {
      toast.error('Download failed')
    }
  }

  const handleShare = async (file: FileEntity) => {
    try {
      const res = await shareApi.create({
        fileId: file.id,
        allowPreview: true,
        expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
      })
      const token = res.data.data.shareToken
      const link = `${window.location.origin}/shared/${token}`
      setShareLink(link)
      setShareModal(file)
    } catch {
      toast.error('Failed to create share link')
    }
  }

  const handleRename = async () => {
    if (!renameModal || !renameValue.trim()) return
    try {
      await fileApi.rename(renameModal.id, renameValue)
      toast.success('File renamed')
      setRenameModal(null)
      loadFiles()
    } catch {
      toast.error('Rename failed')
    }
  }

  const copyLink = () => {
    navigator.clipboard.writeText(shareLink)
    toast.success('Link copied to clipboard')
  }

  const allItems = [...folders, ...files] as (FolderType | FileEntity)[]

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-white">
            {currentFolderId ? folders.find(f => f.id === currentFolderId)?.name || 'Folder' : 'My Files'}
          </h1>
          <p className="text-slate-400 mt-1">{allItems.length} items</p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => setCreatingFolder(true)} className="btn-secondary">
            <FolderPlus className="w-4 h-4" />
            New Folder
          </button>
          <button onClick={() => navigate('/upload')} className="btn-primary">
            <Upload className="w-4 h-4" />
            Upload
          </button>
        </div>
      </div>

      {/* Toolbar */}
      <div className="flex items-center gap-4">
        {/* Breadcrumb */}
        <button onClick={() => navigate('/files')} className="btn-ghost">
          <Home className="w-4 h-4" />
        </button>

        {/* Search */}
        <form onSubmit={handleSearch} className="flex-1 max-w-md relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search files..."
            className="input-field pl-10"
          />
          {searchQuery && (
            <button type="button" onClick={() => { setSearchQuery(''); loadFiles() }} className="absolute right-3 top-1/2 -translate-y-1/2">
              <X className="w-4 h-4 text-slate-500" />
            </button>
          )}
        </form>

        {/* View toggle */}
        <div className="flex items-center bg-white/5 rounded-lg p-1 border border-white/10">
          <button
            onClick={() => setViewMode('grid')}
            className={`p-2 rounded-md transition-colors ${viewMode === 'grid' ? 'bg-brand-500/20 text-brand-400' : 'text-slate-500'}`}
          >
            <Grid3X3 className="w-4 h-4" />
          </button>
          <button
            onClick={() => setViewMode('list')}
            className={`p-2 rounded-md transition-colors ${viewMode === 'list' ? 'bg-brand-500/20 text-brand-400' : 'text-slate-500'}`}
          >
            <List className="w-4 h-4" />
          </button>
        </div>

        <button onClick={loadFiles} className="btn-ghost">
          <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
        </button>
      </div>

      {/* New folder form */}
      <AnimatePresence>
        {creatingFolder && (
          <motion.form
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            onSubmit={handleCreateFolder}
            className="flex gap-3"
          >
            <input
              type="text"
              value={newFolderName}
              onChange={(e) => setNewFolderName(e.target.value)}
              placeholder="Folder name..."
              className="input-field flex-1"
              autoFocus
            />
            <button type="submit" className="btn-primary">Create</button>
            <button type="button" onClick={() => setCreatingFolder(false)} className="btn-secondary">Cancel</button>
          </motion.form>
        )}
      </AnimatePresence>

      {/* File list */}
      {isLoading ? (
        <div className="flex items-center justify-center py-20">
          <Loader2 className="w-8 h-8 text-brand-400 animate-spin" />
        </div>
      ) : allItems.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 border border-dashed border-white/10 rounded-2xl">
          <div className="w-16 h-16 rounded-2xl bg-white/5 flex items-center justify-center mb-4">
            <FileText className="w-8 h-8 text-slate-600" />
          </div>
          <h3 className="text-lg font-medium text-slate-300 mb-1">No files here</h3>
          <p className="text-sm text-slate-500 mb-4">Drop files here or use the upload button</p>
          <button onClick={() => navigate('/upload')} className="btn-primary">
            <Upload className="w-4 h-4" />
            Upload Files
          </button>
        </div>
      ) : viewMode === 'grid' ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 2xl:grid-cols-8 gap-3">
          {/* Folders */}
          {folders.map((folder, i) => (
            <motion.div
              key={folder.id}
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ delay: i * 0.02 }}
              onClick={() => navigate(`/files/${folder.id}`)}
              className="group p-4 rounded-2xl bg-white/[0.03] border border-white/[0.06] hover:bg-white/[0.06] hover:border-brand-500/30 cursor-pointer transition-all"
            >
              <div className="flex flex-col items-center text-center">
                <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-amber-500/20 to-orange-500/20 flex items-center justify-center mb-3">
                  <FolderIcon className="w-7 h-7 text-amber-400" />
                </div>
                <p className="text-sm font-medium text-slate-200 truncate w-full">{folder.name}</p>
                <p className="text-xs text-slate-500 mt-1">{folder.fileCount} files</p>
              </div>
            </motion.div>
          ))}

          {/* Files */}
          {files.map((file, i) => (
            <motion.div
              key={file.id}
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ delay: (folders.length + i) * 0.02 }}
              className="group p-4 rounded-2xl bg-white/[0.03] border border-white/[0.06] hover:bg-white/[0.06] hover:border-brand-500/30 transition-all relative"
            >
              <div className="flex flex-col items-center text-center">
                <div className={`w-14 h-14 rounded-2xl flex items-center justify-center mb-3 ${getFileColor(file.extension || '')}`}>
                  <FileText className="w-7 h-7" />
                </div>
                <p className="text-sm font-medium text-slate-200 truncate w-full" title={file.name}>{file.name}</p>
                <p className="text-xs text-slate-500 mt-1">{formatBytes(file.fileSizeBytes)}</p>
              </div>

              {/* Actions overlay */}
              <div className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity flex gap-1">
                <button
                  onClick={(e) => { e.stopPropagation(); handleDownload(file) }}
                  className="p-1.5 rounded-lg bg-slate-800/90 text-slate-300 hover:text-white transition-colors"
                >
                  <Download className="w-3.5 h-3.5" />
                </button>
                <button
                  onClick={(e) => { e.stopPropagation(); handleShare(file) }}
                  className="p-1.5 rounded-lg bg-slate-800/90 text-slate-300 hover:text-brand-400 transition-colors"
                >
                  <Link2 className="w-3.5 h-3.5" />
                </button>
                <button
                  onClick={(e) => { e.stopPropagation(); setContextMenu({ x: e.clientX, y: e.clientY, item: file }); setSelectedItems(new Set([file.id])) }}
                  className="p-1.5 rounded-lg bg-slate-800/90 text-slate-300 hover:text-white transition-colors"
                >
                  <MoreVertical className="w-3.5 h-3.5" />
                </button>
              </div>

              {file.isShared && (
                <div className="absolute top-2 left-2">
                  <div className="px-1.5 py-0.5 rounded bg-brand-500/20 text-brand-400 text-xs">Shared</div>
                </div>
              )}
            </motion.div>
          ))}
        </div>
      ) : (
        /* List view */
        <div className="space-y-1">
          {[...folders, ...files].map((item, i) => (
            <motion.div
              key={item.id}
              initial={{ opacity: 0, x: -10 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: i * 0.02 }}
              className="flex items-center gap-4 p-3 rounded-xl hover:bg-white/[0.03] group transition-colors cursor-pointer"
              onClick={() => 'parentFolderId' in item && navigate(`/files/${item.id}`)}
            >
              <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${
                'parentFolderId' in item ? 'bg-amber-500/20' : getFileColor((item as FileEntity).extension || '')
              }`}>
                {'parentFolderId' in item ? (
                  <FolderIcon className="w-5 h-5 text-amber-400" />
                ) : (
                  <FileText className={`w-5 h-5 ${getFileColor((item as FileEntity).extension || '').split(' ')[1]}`} />
                )}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-slate-200 truncate">{item.name}</p>
                <p className="text-xs text-slate-500">
                  {'parentFolderId' in item
                    ? `${item.fileCount} items`
                    : formatBytes((item as FileEntity).fileSizeBytes)
                  } · {formatDate(item.updatedAt || item.createdAt)}
                </p>
              </div>
              <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                <button onClick={(e) => { e.stopPropagation(); handleDownload(item as FileEntity) }} className="p-2 rounded-lg hover:bg-white/5 text-slate-400">
                  <Download className="w-4 h-4" />
                </button>
                <button onClick={(e) => { e.stopPropagation(); handleShare(item as FileEntity) }} className="p-2 rounded-lg hover:bg-white/5 text-slate-400">
                  <Link2 className="w-4 h-4" />
                </button>
              </div>
            </motion.div>
          ))}
        </div>
      )}

      {/* Share Modal */}
      <AnimatePresence>
        {shareModal && (
          <>
            <motion.div
              initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
              onClick={() => setShareModal(null)}
              className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50"
            />
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 10 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 10 }}
              className="fixed inset-0 z-50 flex items-center justify-center p-4"
            >
              <div className="bg-slate-900 rounded-2xl border border-white/10 p-8 w-full max-w-md shadow-2xl">
                <h3 className="text-xl font-semibold text-white mb-2">Share "{shareModal.name}"</h3>
                <p className="text-sm text-slate-400 mb-6">Anyone with this link can access the file</p>

                <div className="flex gap-2 mb-4">
                  <input
                    type="text"
                    value={shareLink}
                    readOnly
                    className="input-field font-mono text-sm"
                  />
                  <button onClick={copyLink} className="btn-primary px-4">
                    <Check className="w-4 h-4" />
                    Copy
                  </button>
                </div>

                <div className="flex justify-end gap-2">
                  <button onClick={() => setShareModal(null)} className="btn-secondary">Close</button>
                </div>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>

      {/* Rename Modal */}
      <AnimatePresence>
        {renameModal && (
          <>
            <motion.div
              initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
              onClick={() => setRenameModal(null)}
              className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50"
            />
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="fixed inset-0 z-50 flex items-center justify-center p-4"
            >
              <div className="bg-slate-900 rounded-2xl border border-white/10 p-8 w-full max-w-md">
                <h3 className="text-xl font-semibold text-white mb-6">Rename File</h3>
                <input
                  type="text"
                  value={renameValue}
                  onChange={(e) => setRenameValue(e.target.value)}
                  className="input-field mb-6"
                  autoFocus
                />
                <div className="flex justify-end gap-2">
                  <button onClick={() => setRenameModal(null)} className="btn-secondary">Cancel</button>
                  <button onClick={handleRename} className="btn-primary">Rename</button>
                </div>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
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

function formatDate(date: string): string {
  try {
    return format(new Date(date), 'MMM d, yyyy')
  } catch {
    return ''
  }
}

function getFileColor(ext: string): string {
  const colors: Record<string, string> = {
    pdf: 'bg-red-500/20 text-red-400',
    doc: 'bg-blue-500/20 text-blue-400',
    docx: 'bg-blue-500/20 text-blue-400',
    xls: 'bg-green-500/20 text-green-400',
    xlsx: 'bg-green-500/20 text-green-400',
    jpg: 'bg-purple-500/20 text-purple-400',
    jpeg: 'bg-purple-500/20 text-purple-400',
    png: 'bg-purple-500/20 text-purple-400',
    gif: 'bg-pink-500/20 text-pink-400',
    mp4: 'bg-orange-500/20 text-orange-400',
    mp3: 'bg-yellow-500/20 text-yellow-400',
    zip: 'bg-amber-500/20 text-amber-400',
    txt: 'bg-slate-500/20 text-slate-400',
  }
  return colors[ext.toLowerCase()] || 'bg-slate-500/20 text-slate-400'
}
