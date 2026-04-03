import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Cloud, Eye, EyeOff, ArrowRight, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { useAuthStore } from '../stores/authStore'

export default function LoginPage() {
  const navigate = useNavigate()
  const { login, isLoading } = useAuthStore()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!email || !password) {
      toast.error('Please fill in all fields')
      return
    }
    try {
      await login(email, password)
      toast.success('Welcome back!')
      navigate('/')
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Login failed. Please check your credentials.')
    }
  }

  return (
    <div className="min-h-screen flex">
      {/* Left side - Branding */}
      <div className="hidden lg:flex lg:w-1/2 relative overflow-hidden">
        {/* Background gradient */}
        <div className="absolute inset-0 bg-gradient-to-br from-brand-950 via-brand-900 to-slate-950" />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-brand-500/20 via-transparent to-transparent" />

        {/* Grid pattern */}
        <div className="absolute inset-0 opacity-[0.03]" style={{
          backgroundImage: 'linear-gradient(rgba(255,255,255,0.1) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.1) 1px, transparent 1px)',
          backgroundSize: '60px 60px'
        }} />

        {/* Floating orbs */}
        <motion.div
          animate={{ y: [-10, 10, -10] }}
          transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut' }}
          className="absolute top-1/4 left-1/4 w-96 h-96 rounded-full bg-brand-500/10 blur-3xl"
        />
        <motion.div
          animate={{ y: [10, -10, 10] }}
          transition={{ duration: 8, repeat: Infinity, ease: 'easeInOut' }}
          className="absolute bottom-1/4 right-1/4 w-64 h-64 rounded-full bg-brand-400/10 blur-3xl"
        />

        {/* Content */}
        <div className="relative z-10 flex flex-col justify-center p-16">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 }}
          >
            <div className="flex items-center gap-4 mb-12">
              <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-brand-400 to-brand-600 flex items-center justify-center shadow-2xl shadow-brand-500/30">
                <Cloud className="w-7 h-7 text-white" />
              </div>
              <div>
                <h1 className="text-4xl font-bold text-white">CloudSync</h1>
                <p className="text-slate-400">Secure File Storage</p>
              </div>
            </div>

            <h2 className="text-5xl font-bold text-white leading-tight mb-6">
              Your files,<br />
              <span className="bg-gradient-to-r from-brand-400 to-brand-200 bg-clip-text text-transparent">
                secure forever.
              </span>
            </h2>

            <p className="text-slate-400 text-lg max-w-md leading-relaxed mb-12">
              Enterprise-grade file storage with resumable uploads, smart sharing, and bulletproof security.
            </p>

            <div className="flex items-center gap-8 text-sm text-slate-500">
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 rounded-full bg-brand-400" />
                <span>End-to-end encryption</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 rounded-full bg-brand-400" />
                <span>Resumable uploads</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 rounded-full bg-brand-400" />
                <span>Smart sharing</span>
              </div>
            </div>
          </motion.div>
        </div>
      </div>

      {/* Right side - Login form */}
      <div className="flex-1 flex items-center justify-center p-6 lg:p-16">
        <motion.div
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          className="w-full max-w-md"
        >
          {/* Mobile logo */}
          <div className="lg:hidden flex items-center gap-3 mb-8">
            <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-brand-500 to-brand-600 flex items-center justify-center shadow-lg shadow-brand-500/30">
              <Cloud className="w-6 h-6 text-white" />
            </div>
            <span className="text-2xl font-bold text-white">CloudSync</span>
          </div>

          <h2 className="text-3xl font-bold text-white mb-2">Welcome back</h2>
          <p className="text-slate-400 mb-8">Sign in to your account to continue</p>

          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-2">Email</label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@company.com"
                className="input-field"
                autoComplete="email"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300 mb-2">Password</label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="input-field pr-12"
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 p-1 text-slate-500 hover:text-slate-300 transition-colors"
                >
                  {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={isLoading}
              className="btn-primary w-full h-12 text-base"
            >
              {isLoading ? (
                <Loader2 className="w-5 h-5 animate-spin" />
              ) : (
                <>
                  Sign In
                  <ArrowRight className="w-5 h-5" />
                </>
              )}
            </button>
          </form>

          <p className="text-center text-sm text-slate-400 mt-8">
            Don't have an account?{' '}
            <Link to="/register" className="text-brand-400 hover:text-brand-300 font-medium transition-colors">
              Create account
            </Link>
          </p>
        </motion.div>
      </div>
    </div>
  )
}
