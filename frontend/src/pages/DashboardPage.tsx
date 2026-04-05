import { useEffect } from 'react'
import { motion } from 'framer-motion'
import {
  HardDrive, FolderOpen, FileText, Link2, TrendingUp, Clock,
  Download, ArrowUpRight, Sparkles
} from 'lucide-react'
import { fileApi } from '../services/api'
import { useFileStore } from '../stores/fileStore'
import { useAuthStore } from '../stores/authStore'
import { format } from 'date-fns'

export default function DashboardPage() {
  const { dashboard, setDashboard, isLoading, setLoading } = useFileStore()
  const { user } = useAuthStore()

  useEffect(() => {
    loadDashboard()
  }, [])

  const loadDashboard = async () => {
    setLoading(true)
    try {
      const res = await fileApi.getDashboard()
      setDashboard(res.data.data)
    } catch (err) {
      console.error('Failed to load dashboard:', err)
    } finally {
      setLoading(false)
    }
  }

  const statCards = [
    {
      label: 'Total Files',
      value: dashboard?.totalFiles || 0,
      icon: FileText,
      color: 'from-blue-500 to-cyan-500',
      bg: 'bg-blue-500/10',
      border: 'border-blue-500/20',
    },
    {
      label: 'Folders',
      value: dashboard?.totalFolders || 0,
      icon: FolderOpen,
      color: 'from-purple-500 to-pink-500',
      bg: 'bg-purple-500/10',
      border: 'border-purple-500/20',
    },
    {
      label: 'Active Shares',
      value: dashboard?.activeShares || 0,
      icon: Link2,
      color: 'from-orange-500 to-amber-500',
      bg: 'bg-orange-500/10',
      border: 'border-orange-500/20',
    },
    {
      label: 'Total Downloads',
      value: dashboard?.totalDownloads || 0,
      icon: Download,
      color: 'from-emerald-500 to-teal-500',
      bg: 'bg-emerald-500/10',
      border: 'border-emerald-500/20',
    },
  ]

  return (
    <div className="space-y-8">
      {/* Header */}
      <motion.div
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex items-center justify-between"
      >
        <div>
          <h1 className="text-3xl font-bold text-white">
            Good {getTimeOfDay()}, {user?.firstName || 'there'}!
          </h1>
          <p className="text-slate-400 mt-1">Here's your storage overview</p>
        </div>
        <div className="hidden sm:flex items-center gap-2 px-4 py-2 rounded-xl bg-brand-500/10 border border-brand-500/20">
          <Sparkles className="w-4 h-4 text-brand-400" />
          <span className="text-sm text-brand-300">All systems operational</span>
        </div>
      </motion.div>

      {/* Storage card */}
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
        className="rounded-2xl p-8 relative overflow-hidden"
        style={{
          background: 'linear-gradient(135deg, rgba(90, 107, 255, 0.1) 0%, rgba(139, 92, 246, 0.05) 100%)',
          border: '1px solid rgba(90, 107, 255, 0.2)',
        }}
      >
        <div className="absolute top-0 right-0 w-96 h-96 rounded-full bg-brand-500/5 blur-3xl -translate-y-1/2 translate-x-1/2" />

        <div className="relative flex flex-col lg:flex-row lg:items-center gap-8">
          <div className="flex-1">
            <div className="flex items-center gap-3 mb-4">
              <div className="p-2 rounded-lg bg-brand-500/20">
                <HardDrive className="w-5 h-5 text-brand-400" />
              </div>
              <h2 className="text-lg font-semibold text-white">Storage Usage</h2>
            </div>

            <div className="flex items-baseline gap-2 mb-6">
              <span className="text-4xl font-bold text-white">
                {formatBytes(dashboard?.totalStorageUsedBytes || 0)}
              </span>
              <span className="text-slate-400">
                / {formatBytes(dashboard?.totalStorageQuotaBytes || 0)}
              </span>
            </div>

            <div className="storage-bar h-3 mb-4">
              <motion.div
                initial={{ width: 0 }}
                animate={{ width: `${Math.min(dashboard?.usagePercentage || 0, 100)}%` }}
                transition={{ duration: 1, ease: 'easeOut', delay: 0.3 }}
                className="h-full rounded-full bg-gradient-to-r from-brand-500 via-brand-400 to-brand-300"
              />
            </div>

            <div className="flex items-center justify-between text-sm">
              <span className="text-slate-400">
                {dashboard?.usagePercentage?.toFixed(1)}% used
              </span>
              <span className="text-slate-400">
                {formatBytes((dashboard?.totalStorageQuotaBytes || 0) - (dashboard?.totalStorageUsedBytes || 0))} available
              </span>
            </div>
          </div>

          {/* Stats */}
          <div className="grid grid-cols-2 gap-4 lg:gap-6">
            {statCards.map((card, i) => (
              <motion.div
                key={card.label}
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ delay: 0.2 + i * 0.05 }}
                className={`p-4 rounded-xl ${card.bg} border ${card.border}`}
              >
                <div className="flex items-center gap-3 mb-2">
                  <card.icon className="w-4 h-4 text-slate-400" />
                  <span className="text-sm text-slate-400">{card.label}</span>
                </div>
                <p className="text-2xl font-bold text-white">{card.value.toLocaleString()}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </motion.div>

      {/* Recent Files */}
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.3 }}
      >
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-semibold text-white flex items-center gap-2">
            <Clock className="w-5 h-5 text-slate-400" />
            Recent Files
          </h2>
          <a href="/files" className="text-sm text-brand-400 hover:text-brand-300 flex items-center gap-1 transition-colors">
            View all
            <ArrowUpRight className="w-4 h-4" />
          </a>
        </div>

        {dashboard?.recentFiles && dashboard.recentFiles.length > 0 ? (
          <div className="grid gap-3">
            {dashboard.recentFiles.map((file, i) => (
              <motion.div
                key={file.id}
                initial={{ opacity: 0, x: -10 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.4 + i * 0.05 }}
                className="flex items-center gap-4 p-4 rounded-xl bg-white/[0.03] border border-white/[0.05] hover:bg-white/[0.06] transition-colors group"
              >
                <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${
                  getFileColor(file.extension || '').bg
                }`}>
                  <FileText className={`w-5 h-5 ${getFileColor(file.extension || '').text}`} />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-white truncate group-hover:text-brand-300 transition-colors">
                    {file.name}
                  </p>
                  <p className="text-xs text-slate-500 mt-0.5">
                    {file.folderPath || 'Root'} · {formatBytes(file.fileSizeBytes)} · {formatDate(file.createdAt)}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-xs text-slate-400">{formatDate(file.createdAt)}</p>
                  <p className="text-xs text-slate-500 mt-0.5">{file.downloadCount || 0} downloads</p>
                </div>
              </motion.div>
            ))}
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center py-16 rounded-2xl border border-dashed border-white/10">
            <div className="w-16 h-16 rounded-2xl bg-white/5 flex items-center justify-center mb-4">
              <FileText className="w-8 h-8 text-slate-600" />
            </div>
            <h3 className="text-lg font-medium text-slate-300 mb-1">No files yet</h3>
            <p className="text-sm text-slate-500 mb-4">Upload your first file to get started</p>
            <a href="/upload" className="btn-primary">
              Upload Files
            </a>
          </div>
        )}
      </motion.div>
    </div>
  )
}

function getTimeOfDay(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'morning'
  if (hour < 17) return 'afternoon'
  return 'evening'
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
    return format(new Date(date), 'MMM d, h:mm a')
  } catch {
    return ''
  }
}

function getFileColor(ext: string): { bg: string; text: string } {
  const colors: Record<string, { bg: string; text: string }> = {
    pdf: { bg: 'bg-red-500/20', text: 'text-red-400' },
    doc: { bg: 'bg-blue-500/20', text: 'text-blue-400' },
    docx: { bg: 'bg-blue-500/20', text: 'text-blue-400' },
    xls: { bg: 'bg-green-500/20', text: 'text-green-400' },
    xlsx: { bg: 'bg-green-500/20', text: 'text-green-400' },
    jpg: { bg: 'bg-purple-500/20', text: 'text-purple-400' },
    jpeg: { bg: 'bg-purple-500/20', text: 'text-purple-400' },
    png: { bg: 'bg-purple-500/20', text: 'text-purple-400' },
    gif: { bg: 'bg-pink-500/20', text: 'text-pink-400' },
    mp4: { bg: 'bg-orange-500/20', text: 'text-orange-400' },
    mp3: { bg: 'bg-yellow-500/20', text: 'text-yellow-400' },
    zip: { bg: 'bg-amber-500/20', text: 'text-amber-400' },
    txt: { bg: 'bg-slate-500/20', text: 'text-slate-400' },
  }
  return colors[ext.toLowerCase()] || { bg: 'bg-slate-500/20', text: 'text-slate-400' }
}
