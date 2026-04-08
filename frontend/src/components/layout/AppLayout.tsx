import { Outlet, NavLink, useLocation } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import {
  LayoutDashboard, FolderOpen, Upload, Link2, LogOut, Settings,
  ChevronRight, Cloud, Menu, X, Bell, Search
} from 'lucide-react'
import { useState } from 'react'
import { useAuthStore } from '../../stores/authStore'
import { useFileStore } from '../../stores/fileStore'

const navItems = [
  { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/files', icon: FolderOpen, label: 'My Files' },
  { to: '/upload', icon: Upload, label: 'Upload' },
  { to: '/shared', icon: Link2, label: 'Shared' },
]

export default function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [mobileOpen, setMobileOpen] = useState(false)
  const location = useLocation()
  const { user, logout } = useAuthStore()
  const { uploads } = useFileStore()

  const activeUploads = Array.from(uploads.values()).filter(u => u.progress < 100)

  return (
    <div className="flex h-screen overflow-hidden">
      {/* Desktop Sidebar */}
      <aside
        className={`hidden lg:flex flex-col bg-slate-900/50 border-r border-white/5 transition-all duration-300 ${
          sidebarOpen ? 'w-64' : 'w-20'
        }`}
      >
        <div className="flex items-center gap-3 p-5 border-b border-white/5">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-brand-500 to-brand-600 flex items-center justify-center shadow-lg shadow-brand-500/20 flex-shrink-0">
            <Cloud className="w-5 h-5 text-white" />
          </div>
          {sidebarOpen && (
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex flex-col">
              <h1 className="text-white font-bold text-lg leading-tight">CloudSync</h1>
              <p className="text-slate-500 text-xs">{user?.organizationName || 'Personal'}</p>
            </motion.div>
          )}
        </div>

        <nav className="flex-1 p-3 space-y-1">
          {navItems.map(({ to, icon: Icon, label }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all duration-200 ${
                  isActive
                    ? 'bg-brand-500/15 text-brand-400 shadow-lg shadow-brand-500/10'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-white/5'
                }`
              }
            >
              <Icon className="w-5 h-5 flex-shrink-0" />
              {sidebarOpen && <span>{label}</span>}
            </NavLink>
          ))}
        </nav>

        {/* Storage indicator */}
        {sidebarOpen && (
          <div className="p-4 mx-3 mb-3 rounded-xl bg-white/5 border border-white/5">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs text-slate-400">Storage</span>
              <span className="text-xs text-slate-300 font-mono">
                {formatBytes(useFileStore.getState().dashboard?.totalStorageUsedBytes || 0)}
              </span>
            </div>
            <div className="storage-bar">
              <div
                className="storage-fill"
                style={{ width: `${useFileStore.getState().dashboard?.usagePercentage || 0}%` }}
              />
            </div>
          </div>
        )}

        {/* User section */}
        <div className="p-3 border-t border-white/5">
          <div className={`flex items-center gap-3 ${sidebarOpen ? '' : 'justify-center'}`}>
            <div className="w-9 h-9 rounded-full bg-gradient-to-br from-brand-400 to-brand-600 flex items-center justify-center text-white font-semibold text-sm flex-shrink-0">
              {user?.firstName?.[0] || user?.email?.[0]?.toUpperCase() || 'U'}
            </div>
            {sidebarOpen && (
              <div className="flex-1 min-w-0">
                <p className="text-sm text-slate-200 truncate">{user?.firstName} {user?.lastName}</p>
                <p className="text-xs text-slate-500 truncate">{user?.email}</p>
              </div>
            )}
            {sidebarOpen && (
              <button
                onClick={logout}
                className="p-2 rounded-lg text-slate-500 hover:text-slate-300 hover:bg-white/5 transition-colors"
              >
                <LogOut className="w-4 h-4" />
              </button>
            )}
          </div>
        </div>

        {/* Collapse toggle */}
        <button
          onClick={() => setSidebarOpen(!sidebarOpen)}
          className="hidden lg:flex absolute top-20 -right-3 w-6 h-6 rounded-full bg-slate-800 border border-white/10 items-center justify-center text-slate-400 hover:text-white hover:bg-slate-700 transition-colors z-10"
        >
          <ChevronRight className={`w-3 h-3 transition-transform ${sidebarOpen ? 'rotate-180' : ''}`} />
        </button>
      </aside>

      {/* Mobile header */}
      <div className="lg:hidden fixed top-0 left-0 right-0 z-50 flex items-center justify-between px-4 h-16 bg-slate-950/90 backdrop-blur-xl border-b border-white/5">
        <button onClick={() => setMobileOpen(true)} className="p-2 rounded-lg hover:bg-white/5 transition-colors">
          <Menu className="w-5 h-5 text-slate-300" />
        </button>
        <div className="flex items-center gap-2">
          <Cloud className="w-6 h-6 text-brand-400" />
          <span className="text-white font-bold">CloudSync</span>
        </div>
        <button className="p-2 rounded-lg hover:bg-white/5 transition-colors relative">
          <Bell className="w-5 h-5 text-slate-300" />
          {activeUploads.length > 0 && (
            <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-brand-500" />
          )}
        </button>
      </div>

      {/* Mobile sidebar overlay */}
      <AnimatePresence>
        {mobileOpen && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setMobileOpen(false)}
              className="lg:hidden fixed inset-0 bg-black/60 backdrop-blur-sm z-50"
            />
            <motion.aside
              initial={{ x: -280 }}
              animate={{ x: 0 }}
              exit={{ x: -280 }}
              transition={{ type: 'spring', damping: 25, stiffness: 300 }}
              className="lg:hidden fixed top-0 left-0 bottom-0 w-72 bg-slate-900 border-r border-white/5 z-50 flex flex-col"
            >
              <div className="flex items-center justify-between p-5 border-b border-white/5">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-brand-500 to-brand-600 flex items-center justify-center">
                    <Cloud className="w-5 h-5 text-white" />
                  </div>
                  <div>
                    <h1 className="text-white font-bold text-lg">CloudSync</h1>
                    <p className="text-slate-500 text-xs">{user?.organizationName || 'Personal'}</p>
                  </div>
                </div>
                <button onClick={() => setMobileOpen(false)} className="p-2 rounded-lg hover:bg-white/5">
                  <X className="w-5 h-5 text-slate-400" />
                </button>
              </div>
              <nav className="flex-1 p-4 space-y-1">
                {navItems.map(({ to, icon: Icon, label }) => (
                  <NavLink
                    key={to}
                    to={to}
                    end={to === '/'}
                    onClick={() => setMobileOpen(false)}
                    className={({ isActive }) =>
                      `flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition-all ${
                        isActive
                          ? 'bg-brand-500/15 text-brand-400'
                          : 'text-slate-400 hover:bg-white/5'
                      }`
                    }
                  >
                    <Icon className="w-5 h-5" />
                    {label}
                  </NavLink>
                ))}
              </nav>
              <div className="p-4 border-t border-white/5">
                <button
                  onClick={logout}
                  className="flex items-center gap-3 w-full px-4 py-3 rounded-xl text-slate-400 hover:bg-white/5 transition-colors"
                >
                  <LogOut className="w-5 h-5" />
                  <span className="text-sm">Sign Out</span>
                </button>
              </div>
            </motion.aside>
          </>
        )}
      </AnimatePresence>

      {/* Main content */}
      <main className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Top bar */}
        <header className="hidden lg:flex items-center justify-between px-8 h-16 border-b border-white/5 bg-slate-950/50">
          <div className="flex items-center gap-2 flex-1">
            <div className="relative max-w-md w-full">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
              <input
                type="text"
                placeholder="Search files, folders..."
                className="w-full pl-10 pr-4 py-2 rounded-xl bg-white/5 border border-white/10 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 focus:border-brand-500/30 transition-all"
              />
            </div>
          </div>
          <div className="flex items-center gap-3 ml-4">
            {/* Breadcrumb */}
            {useFileStore.getState().folderPath.length > 0 && (
              <div className="flex items-center gap-1 text-sm text-slate-400">
                {useFileStore.getState().folderPath.map((f, i) => (
                  <span key={f.id} className="flex items-center">
                    {i > 0 && <ChevronRight className="w-3 h-3 mx-1" />}
                    <button className="hover:text-white transition-colors">{f.name}</button>
                  </span>
                ))}
              </div>
            )}
          </div>
        </header>

        {/* Page content */}
        <div className="flex-1 overflow-auto p-6 lg:p-8 pt-20 lg:pt-8">
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.2 }}
            >
              <Outlet />
            </motion.div>
          </AnimatePresence>
        </div>
      </main>
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
