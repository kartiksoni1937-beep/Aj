import React, { useState, useEffect, useRef } from 'react';
import { ViewState } from '../types';
import { Button, Input } from '../components/UI';
import { Lock, User, ArrowLeft, ShieldCheck, X } from 'lucide-react';
import { authApi, discordApi, setToken } from '../api';

interface AuthProps {
  view: 'LOGIN' | 'REGISTER';
  onNavigate: (view: ViewState) => void;
}

export const Auth: React.FC<AuthProps> = ({ view, onNavigate }) => {
  const isLogin = view === 'LOGIN';
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [showForgotPassword, setShowForgotPassword] = useState(false);
  const [discordLoginLoading, setDiscordLoginLoading] = useState(false);
  const [turnstileToken, setTurnstileToken] = useState<string>('');
  const turnstileWidgetRef = useRef<string | null>(null);
  const turnstileContainerRef = useRef<HTMLDivElement>(null);

  // Check for Discord login errors in URL
  useEffect(() => {
    const urlParams = new URLSearchParams(window.location.search);
    const error = urlParams.get('error');
    const message = urlParams.get('message');
    
    if (error && isLogin) {
      setError(message || 'Discord login failed');
      // Clean URL
      window.history.replaceState({}, '', window.location.pathname);
    }
  }, [isLogin]);

  // Initialize Turnstile widget for registration
  useEffect(() => {
    if (!isLogin && turnstileContainerRef.current && typeof window !== 'undefined' && (window as any).turnstile) {
      // Clear any existing widget
      if (turnstileWidgetRef.current) {
        (window as any).turnstile.remove(turnstileWidgetRef.current);
      }

      // Render Turnstile widget
      const widgetId = (window as any).turnstile.render(turnstileContainerRef.current, {
        sitekey: '0x4AAAAAACFZupE561D6o7L3',
        callback: (token: string) => {
          setTurnstileToken(token);
        },
        'error-callback': () => {
          setTurnstileToken('');
        },
        'expired-callback': () => {
          setTurnstileToken('');
        },
      });

      turnstileWidgetRef.current = widgetId;
    }

    return () => {
      // Cleanup widget when component unmounts or view changes
      if (turnstileWidgetRef.current && typeof window !== 'undefined' && (window as any).turnstile) {
        (window as any).turnstile.remove(turnstileWidgetRef.current);
        turnstileWidgetRef.current = null;
      }
    };
  }, [isLogin]);

  const handleDiscordLogin = async () => {
    setError('');
    setDiscordLoginLoading(true);
    
    try {
      const response = await discordApi.getLoginUrl();
      if (response.success && response.authUrl) {
        // Redirect to Discord OAuth
        window.location.href = response.authUrl;
      } else {
        setError(response.message || 'Failed to initiate Discord login');
        setDiscordLoginLoading(false);
      }
    } catch (err) {
      setError('An unexpected error occurred');
      setDiscordLoginLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      if (isLogin) {
        // Login
        const response = await authApi.login({
          username,
          password,
        });

        if (response.success) {
          setTimeout(() => {
            onNavigate('DASHBOARD');
          }, 500);
        } else {
          setError(response.message || 'Login failed');
        }
      } else {
        // Register
        if (!username || !password) {
          setError('All fields are required');
          setLoading(false);
          return;
        }

        if (!turnstileToken) {
          setError('Please complete the verification');
          setLoading(false);
          return;
        }

        const response = await authApi.register({
          username,
          password,
          turnstileToken,
        });

        if (response.success) {
          // Reset Turnstile widget
          if (turnstileWidgetRef.current && typeof window !== 'undefined' && (window as any).turnstile) {
            (window as any).turnstile.reset(turnstileWidgetRef.current);
            setTurnstileToken('');
          }

          // Auto-login after registration
          const loginResponse = await authApi.login({
            username,
            password,
          });

          if (loginResponse.success) {
            setTimeout(() => {
              onNavigate('DASHBOARD');
            }, 500);
          } else {
            setError('Account created but login failed. Please try logging in.');
          }
        } else {
          setError(response.message || 'Registration failed');
          // Reset Turnstile on error
          if (turnstileWidgetRef.current && typeof window !== 'undefined' && (window as any).turnstile) {
            (window as any).turnstile.reset(turnstileWidgetRef.current);
            setTurnstileToken('');
          }
        }
      }
    } catch (err) {
      setError('An unexpected error occurred');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-echo-bg relative overflow-hidden">
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-blue-900/10 via-echo-bg to-echo-bg opacity-40 pointer-events-none"></div>
      <div className="w-full max-w-md mx-auto relative z-10">
        <button
          onClick={() => onNavigate('LANDING')}
          className="mb-8 flex items-center text-sm text-echo-muted hover:text-echo-primary transition-colors"
        >
          <ArrowLeft className="w-4 h-4 mr-2" /> Back to Home
        </button>
        <div className="bg-white/[0.02] backdrop-blur-md border border-white/5 rounded-2xl shadow-xl overflow-hidden">
          <div className="p-8 md:p-10">
            <h2 className="font-sans font-bold text-3xl text-white tracking-tighter mb-8 text-center">
              [ ECHO ]
            </h2>
            <form onSubmit={handleSubmit} className="space-y-4">
              {error && (
                <div className="p-4 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-sm">
                  {error}
                </div>
              )}
              <Input
                type="text"
                label=""
                placeholder="Username"
                icon={<User className="w-4 h-4 text-echo-muted" />}
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                disabled={loading}
              />
              <Input
                type="password"
                label=""
                placeholder="Password"
                icon={<Lock className="w-4 h-4 text-echo-muted" />}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                disabled={loading}
              />
              {isLogin && (
                <button
                  type="button"
                  onClick={() => setShowForgotPassword(true)}
                  className="text-sm text-echo-primary hover:text-white transition-colors text-right w-full"
                >
                  Forgot Password?
                </button>
              )}
              {!isLogin && (
                <div className="flex justify-center">
                  <div ref={turnstileContainerRef} id="turnstile-widget"></div>
                </div>
              )}
              <Button
                type="submit"
                className="w-full h-12 rounded-xl text-base font-semibold"
                variant="primary"
                disabled={loading || discordLoginLoading || (!isLogin && !turnstileToken)}
              >
                {loading ? 'Processing...' : (isLogin ? 'Sign In' : 'Create Account')}
              </Button>
              
              {isLogin && (
                <>
                  <div className="relative my-4">
                    <div className="absolute inset-0 flex items-center">
                      <div className="w-full border-t border-white/5"></div>
                    </div>
                    <div className="relative flex justify-center text-sm">
                      <span className="px-2 bg-white/[0.02] text-echo-muted">Or continue with</span>
                    </div>
                  </div>
                  
                  <Button
                    type="button"
                    onClick={handleDiscordLogin}
                    className="w-full h-12 rounded-xl text-base font-semibold !bg-[#5865F2] hover:!bg-[#4752C4] !text-white border-0"
                    disabled={loading || discordLoginLoading}
                  >
                    <i className="fa-brands fa-discord mr-2 text-base"></i>
                    {discordLoginLoading ? 'Connecting...' : 'Login with Discord'}
                  </Button>
                </>
              )}
            </form>
            <div className="mt-6 pt-4 border-t border-white/5 text-center">
              <span className="text-echo-muted text-sm">
                {isLogin ? "Don't have an account? " : "Already have an account? "}
              </span>
              {isLogin ? (
                <button onClick={() => onNavigate('REGISTER')} className="text-echo-primary hover:text-white text-sm font-semibold transition-colors">
                  Sign Up
                </button>
              ) : (
                <button onClick={() => onNavigate('LOGIN')} className="text-echo-primary hover:text-white text-sm font-semibold transition-colors">
                  Sign In
                </button>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Forgot Password Modal */}
      {showForgotPassword && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-md flex items-center justify-center p-4 z-50">
          <div className="bg-white/[0.05] backdrop-blur-xl border-2 border-white/10 rounded-2xl shadow-2xl w-full max-w-lg p-8 md:p-10">
            <div className="flex items-center justify-between mb-6">
              <h3 className="text-3xl font-bold text-white">Reset Password</h3>
              <button
                onClick={() => {
                  setShowForgotPassword(false);
                  setError('');
                }}
                className="text-echo-muted hover:text-white transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            
            <p className="text-base text-echo-text mb-8 leading-relaxed">
              To reset your password, please create a support ticket in our Discord server. Our team will assist you with resetting your password.
            </p>

            <div className="space-y-6">
              <div className="p-6 bg-gradient-to-br from-[#5865F2]/20 to-[#5865F2]/10 border-2 border-[#5865F2]/30 rounded-xl hover:border-[#5865F2]/50 transition-all">
                <div className="flex flex-col items-center text-center gap-4">
                  <div className="w-16 h-16 rounded-xl bg-[#5865F2] flex items-center justify-center flex-shrink-0 shadow-lg">
                    <i className="fa-brands fa-discord text-white text-2xl"></i>
                  </div>
                  <div className="flex-1 w-full">
                    <h4 className="text-white font-bold text-lg mb-2">Discord Support</h4>
                    <p className="text-echo-text text-sm mb-4 leading-relaxed">
                      Join our Discord server and create a ticket in the support channel to request a password reset.
                    </p>
                    <Button
                      onClick={() => {
                        window.open('https://discord.gg/C9sSTYjka2', '_blank');
                      }}
                      className="w-full h-11 text-base font-semibold"
                      variant="primary"
                    >
                      <i className="fa-brands fa-discord mr-2 text-lg"></i>
                      Open Discord Server
                    </Button>
                  </div>
                </div>
              </div>

              <div className="p-5 bg-white/5 rounded-xl border border-white/10">
                <p className="text-sm text-white font-semibold mb-3 flex items-center justify-center gap-2">
                  <ShieldCheck className="w-4 h-4 text-echo-primary flex-shrink-0" />
                  What to include in your ticket:
                </p>
                <ul className="text-sm text-echo-text space-y-2 list-none">
                  <li className="flex items-center gap-3">
                    <span className="text-echo-primary text-lg leading-none flex items-center justify-center w-4 h-4">•</span>
                    <span className="flex-1">Your username</span>
                  </li>
                  <li className="flex items-center gap-3">
                    <span className="text-echo-primary text-lg leading-none flex items-center justify-center w-4 h-4">•</span>
                    <span className="flex-1">Reason for password reset</span>
                  </li>
                  <li className="flex items-center gap-3">
                    <span className="text-echo-primary text-lg leading-none flex items-center justify-center w-4 h-4">•</span>
                    <span className="flex-1">Any additional verification information</span>
                  </li>
                </ul>
              </div>

              <Button
                onClick={() => {
                  setShowForgotPassword(false);
                  setError('');
                }}
                className="w-full h-11"
                variant="outline"
              >
                Close
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};