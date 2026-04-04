import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Cloud, Eye, EyeOff, ArrowRight, Loader2, Building2, User } from 'lucide-react'
import { toast } from 'sonner'
import { useAuthStore } from '../stores/authStore'

export default function RegisterPage() {
  const navigate = useNavigate()
  const { register, isLoading } = useAuthStore()
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    firstName: '',
    lastName: '',
    organizationName: '',
  })
  const [showPassword, setShowPassword] = useState(false)
  const [accountType, setAccountType] = useState<'personal' | 'organization'>('personal')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!formData.email || !formData.password) {
      toast.error('Email and password are required')
      return
    }
    if (formData.password.length < 8) {
      toast.error('Password must be at least 8 characters')
      return
    }
    try {
      await register({
        email: formData.email,
        password: formData.password,
        firstName: formData.firstName || undefined,
        lastName: formData.lastName || undefined,
        organizationName: accountType === 'organization' ? formData.organizationName : undefined,
      })
      toast.success('Account created successfully!')
      navigate('/')
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Registration failed. Please try again.')
    }
  }

  return (
    <div className="min-h-screen flex">
      {/* Left side */}
      <div className="hidden lg:flex lg:w-1/2 relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-brand-950 via-brand-900 to-slate-950" />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-brand-500/20 via-transparent to-transparent" />

        <div className="absolute inset-0 opacity-[0.03]" style={{
          backgroundImage: 'linear-gradient(rgba(255,255,255,0.1) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.1) 1px, transparent 1px)',
          backgroundSize: '60px 60px'
        }} />

        <motion.div
          animate={{ y: [-10, 10, -10] }}
          transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut' }}
          className="absolute top-1/3 right-1/4 w-80 h-80 rounded-full bg-brand-500/10 blur-3xl"
        />
        <motion.div
          animate={{ y: [10, -10, 10] }}
          transition={{ duration: 8, repeat: Infinity, ease: 'easeInOut' }}
          className="absolute bottom-1/3 left-1/4 w-72 h-72 rounded-full bg-brand-400/10 blur-3xl"
        />

        <div className="relative z-10 flex flex-col justify-center p-16">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
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
              Store smarter,<br />
              <span className="bg-gradient-to-r from-brand-400 to-brand-200 bg-clip-text text-transparent">
                share better.
              </span>
            </h2>

            <p className="text-slate-400 text-lg max-w-md leading-relaxed mb-12">
              Join thousands of teams using CloudSync for secure, scalable file management.
            </p>

            <div className="grid grid-cols-2 gap-6">
              {[
                { label: '99.9%', desc: 'Uptime SLA' },
                { label: '10GB', desc: 'Max file size' },
                { label: 'AES-256', desc: 'Encryption' },
                { label: '150+', desc: 'Countries' },
              ].map(({ label, desc }) => (
                <div key={desc} className="p-4 rounded-xl bg-white/5 border border-white/5">
                  <p className="text-2xl font-bold text-white">{label}</p>
                  <p className="text-sm text-slate-400">{desc}</p>
                </div>
              ))}
            </div>
          </motion.div>
        </div>
      </div>

      {/* Right side - Register form */}
      <div className="flex-1 flex items-center justify-center p-6 lg:p-16 overflow-auto">
        <motion.div
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          className="w-full max-w-md py-8"
        >
          <div className="lg:hidden flex items-center gap-3 mb-8">
            <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-brand-500 to-brand-600 flex items-center justify-center shadow-lg shadow-brand-500/30">
              <Cloud className="w-6 h-6 text-white" />
            </div>
            <span className="text-2xl font-bold text-white">CloudSync</span>
          </div>

          <h2 className="text-3xl font-bold text-white mb-2">Create account</h2>
          <p className="text-slate-400 mb-6">Get started with CloudSync for free</p>

          {/* Account type selector */}
          <div className="flex gap-2 p-1 rounded-xl bg-white/5 mb-6">
            {(['personal', 'organization'] as const).map((type) => (
              <button
                key={type}
                onClick={() => setAccountType(type)}
                className={`flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded-lg text-sm font-medium transition-all ${
                  accountType === type
                    ? 'bg-brand-500/20 text-brand-300 border border-brand-500/30'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                {type === 'personal' ? (
                  <>
                    <User className="w-4 h-4" />
                    Personal
                  </>
                ) : (
                  <>
                    <Building2 className="w-4 h-4" />
                    Team
                  </>
                )}
              </button>
            ))}
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            {accountType === 'organization' && (
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-2">Organization Name</label>
                <input
                  type="text"
                  value={formData.organizationName}
                  onChange={(e) => setFormData({ ...formData, organizationName: e.target.value })}
                  placeholder="Acme Corp"
                  className="input-field"
                />
              </div>
            )}

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-2">First Name</label>
                <input
                  type="text"
                  value={formData.firstName}
                  onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                  placeholder="John"
                  className="input-field"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-2">Last Name</label>
                <input
                  type="text"
                  value={formData.lastName}
                  onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                  placeholder="Doe"
                  className="input-field"
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300 mb-2">Email</label>
              <input
                type="email"
                value={formData.email}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
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
                  value={formData.password}
                  onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                  placeholder="Min. 8 characters"
                  className="input-field pr-12"
                  autoComplete="new-password"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 p-1 text-slate-500 hover:text-slate-300"
                >
                  {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                </button>
              </div>
              <p className="text-xs text-slate-500 mt-1">Must be at least 8 characters with uppercase, lowercase, and number</p>
            </div>

            <button
              type="submit"
              disabled={isLoading}
              className="btn-primary w-full h-12 text-base mt-4"
            >
              {isLoading ? (
                <Loader2 className="w-5 h-5 animate-spin" />
              ) : (
                <>
                  Create Account
                  <ArrowRight className="w-5 h-5" />
                </>
              )}
            </button>
          </form>

          <p className="text-center text-sm text-slate-400 mt-6">
            Already have an account?{' '}
            <Link to="/login" className="text-brand-400 hover:text-brand-300 font-medium transition-colors">
              Sign in
            </Link>
          </p>
        </motion.div>
      </div>
    </div>
  )
}
