import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import {
  Link2, Clock, Download, ExternalLink, Trash2,
  Copy, Check, Loader2, FileText, Calendar, Eye, Users
} from 'lucide-react'
import { shareApi } from '../services/api'
import { FileShare } from '../types'
import { toast } from 'sonner'
import { format } from 'date-fns'

export default function SharedPage() {
  const [shares, setShares] = useState<FileShare[]>([])
  const [loading, setLoading] = useState(true)
  const [copiedId, setCopiedId] = useState<number | null>(null)

  useEffect(() => {
    loadShares()
  }, [])

  const loadShares = async () => {
    setLoading(true)
    try {
      const res = await shareApi.getMyShares()
      setShares(res.data.data || [])
    } catch {
      toast.error('Failed to load shares')
    } finally {
      setLoading(false)
    }
  }

  const handleCopyLink = async (share: FileShare) => {
    const link = `${window.location.origin}/shared/${share.shareToken}`
    await navigator.clipboard.writeText(link)
    setCopiedId(share.id)
    toast.success('Link copied to clipboard')
    setTimeout(() => setCopiedId(null), 2000)
  }

  const handleDownload = async (share: FileShare) => {
    try {
      const res = await shareApi.downloadShared(share.shareToken)
      const blob = new Blob([res.data])
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = share.fileName
      document.body.appendChild(a)
      a.click()
      window.URL.revokeObjectURL(url)
      document.body.removeChild(a)
    } catch {
      toast.error('Download failed')
    }
  }

  const handleDeactivate = async (shareId: number) => {
    try {
      await shareApi.deactivate(shareId)
      toast.success('Share deactivated')
      loadShares()
    } catch {
      toast.error('Failed to deactivate share')
    }
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold text-white">Shared Files</h1>
        <p className="text-slate-400 mt-1">Manage your shared links</p>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-20">
          <Loader2 className="w-8 h-8 text-brand-400 animate-spin" />
        </div>
      ) : shares.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 border border-dashed border-white/10 rounded-2xl">
          <div className="w-16 h-16 rounded-2xl bg-white/5 flex items-center justify-center mb-4">
            <Link2 className="w-8 h-8 text-slate-600" />
          </div>
          <h3 className="text-lg font-medium text-slate-300 mb-1">No shared files</h3>
          <p className="text-sm text-slate-500">Share a file to see it here</p>
        </div>
      ) : (
        <div className="space-y-4">
          {shares.map((share, i) => (
            <motion.div
              key={share.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.05 }}
              className={`p-6 rounded-2xl bg-white/[0.03] border ${
                share.isExpired || !share.isActive
                  ? 'border-red-500/20 opacity-60'
                  : 'border-white/5'
              }`}
            >
              <div className="flex items-start gap-4">
                <div className={`w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0 ${
                  share.isExpired || !share.isActive ? 'bg-red-500/20' : 'bg-brand-500/20'
                }`}>
                  <FileText className={`w-6 h-6 ${share.isExpired || !share.isActive ? 'text-red-400' : 'text-brand-400'}`} />
                </div>

                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <h3 className="text-base font-semibold text-white truncate">{share.fileName}</h3>
                    {share.isExpired && (
                      <span className="px-2 py-0.5 rounded text-xs bg-red-500/20 text-red-400">Expired</span>
                    )}
                    {!share.isActive && !share.isExpired && (
                      <span className="px-2 py-0.5 rounded text-xs bg-slate-500/20 text-slate-400">Deactivated</span>
                    )}
                    {share.isActive && !share.isExpired && (
                      <span className="px-2 py-0.5 rounded text-xs bg-emerald-500/20 text-emerald-400">Active</span>
                    )}
                  </div>

                  <div className="flex items-center gap-4 text-xs text-slate-400 mb-3">
                    <span className="flex items-center gap-1">
                      <Users className="w-3 h-3" />
                      {share.shareType === 'INTERNAL' ? 'Internal' : 'External'}
                    </span>
                    {share.maxDownloads && (
                      <span>{share.currentDownloads}/{share.maxDownloads} downloads</span>
                    )}
                    {share.viewCount !== undefined && share.viewCount > 0 && (
                      <span className="flex items-center gap-1">
                        <Eye className="w-3 h-3" />
                        {share.viewCount} views
                      </span>
                    )}
                    {share.expiresAt && (
                      <span className="flex items-center gap-1">
                        <Calendar className="w-3 h-3" />
                        Expires {format(new Date(share.expiresAt), 'MMM d, yyyy')}
                      </span>
                    )}
                  </div>

                  {/* Share link */}
                  <div className="flex items-center gap-2 p-3 rounded-xl bg-white/5 border border-white/5 mb-3">
                    <input
                      type="text"
                      value={`${window.location.origin}/shared/${share.shareToken}`}
                      readOnly
                      className="flex-1 bg-transparent text-xs text-slate-400 font-mono outline-none truncate"
                    />
                    <button
                      onClick={() => handleCopyLink(share)}
                      className="p-1.5 rounded-lg hover:bg-white/10 text-slate-400 transition-colors flex-shrink-0"
                    >
                      {copiedId === share.id ? (
                        <Check className="w-4 h-4 text-emerald-400" />
                      ) : (
                        <Copy className="w-4 h-4" />
                      )}
                    </button>
                  </div>

                  {/* Actions */}
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => handleDownload(share)}
                      disabled={!share.isActive || share.isExpired}
                      className="btn-secondary text-sm px-3 py-1.5 disabled:opacity-50"
                    >
                      <Download className="w-3.5 h-3.5" />
                      Download
                    </button>
                    <button
                      onClick={() => handleCopyLink(share)}
                      className="btn-secondary text-sm px-3 py-1.5"
                    >
                      <Copy className="w-3.5 h-3.5" />
                      Copy Link
                    </button>
                    <button
                      onClick={() => handleDeactivate(share.id)}
                      className="btn-ghost text-sm text-red-400 hover:text-red-300 hover:bg-red-500/10 px-3 py-1.5"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      Remove
                    </button>
                  </div>
                </div>
              </div>
            </motion.div>
          ))}
        </div>
      )}
    </div>
  )
}
