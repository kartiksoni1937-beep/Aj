import React, { useState, useEffect } from 'react';
import { Card, Button, Input } from '../components/UI';
import { KeyModal } from '../components/KeyModal';
import { PaymentModal } from '../components/PaymentModal';
import { 
  Download, 
  CheckCircle, 
  Shield, 
  Gamepad2, 
  Terminal,
  ExternalLink,
  Box,
  Feather,
  Layers,
  Info,
  Folder,
  User,
  Hash,
  Calendar,
  RefreshCw,
  Cpu,
  LogOut,
  Loader2,
  Key,
  Play,
  FileDown,
  Copy,
  Lock,
  CreditCard
} from 'lucide-react';
import { authApi, discordApi, releaseApi, getToken, type User as ApiUser, type Release } from '../api';
import { API_BASE_URL } from '../api';
import { ViewState } from '../types';
import { ChangelogModal } from '../components/ChangelogModal';

interface DashboardProps {
  onNavigate?: (view: ViewState) => void;
}

export const Dashboard: React.FC<DashboardProps> = ({ onNavigate }) => {
  const [securityTab, setSecurityTab] = useState<'HWID' | 'PASSWORD'>('HWID');
  const [selectedLauncher, setSelectedLauncher] = useState<'MODRINTH' | 'MC_LAUNCHER' | 'FEATHER' | 'PRISM'>('MC_LAUNCHER');
  const [user, setUser] = useState<ApiUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [hwid, setHwid] = useState<string>('NOT SET');
  const [resetError, setResetError] = useState<string | null>(null);
  const [isResetting, setIsResetting] = useState(false);
  const [isKeyModalOpen, setIsKeyModalOpen] = useState(false);
  const [isPaymentModalOpen, setIsPaymentModalOpen] = useState(false);
  const [showChangePassword, setShowChangePassword] = useState(false);
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [changePasswordError, setChangePasswordError] = useState<string | null>(null);
  const [changePasswordLoading, setChangePasswordLoading] = useState(false);
  const [changePasswordSuccess, setChangePasswordSuccess] = useState(false);
  const [discordLinked, setDiscordLinked] = useState(false);
  const [discordSuccessMessage, setDiscordSuccessMessage] = useState<string | null>(null);
  const [releases, setReleases] = useState<Release[]>([]);
  const [releasesLoading, setReleasesLoading] = useState(false);
  const [selectedRelease, setSelectedRelease] = useState<Release | null>(null);
  const [isChangelogModalOpen, setIsChangelogModalOpen] = useState(false);
  const [timeUntilExpiry, setTimeUntilExpiry] = useState<string>('');
  const [isDownloading, setIsDownloading] = useState(false);

  useEffect(() => {
    // Check if this is a Discord callback redirect
    const urlParams = new URLSearchParams(window.location.search);
    const discordLinkedParam = urlParams.get('discord_linked');
    const username = urlParams.get('username');
    const payment = urlParams.get('payment');
    const sessionId = urlParams.get('session_id');
    
    if (discordLinkedParam === 'true' && username) {
      setDiscordSuccessMessage(`Discord account ${username} linked successfully!`);
      // Clean URL
      window.history.replaceState({}, '', window.location.pathname);
      // Clear message after 5 seconds
      setTimeout(() => setDiscordSuccessMessage(null), 5000);
    }

    // Handle payment success
    if (payment === 'success') {
      const sessionId = urlParams.get('session_id');
      const orderId = urlParams.get('order_id');
      
      // Clean URL
      window.history.replaceState({}, '', window.location.pathname);
      
      // Refresh user data to get updated plan
      if (sessionId || orderId) {
        fetchUserData();
      }
    }

    fetchUserData();
    checkDiscordStatus();
    fetchReleases();
  }, []);

  // Update expiration timer
  useEffect(() => {
    const updateTimer = () => {
      // Don't show a timer for lifetime plans
      if (user?.plan && user.plan.toLowerCase() === 'lifetime') {
        setTimeUntilExpiry('');
        return;
      }

      if (user?.stripe_subscription_status === 'paused') {
        setTimeUntilExpiry('');
        return;
      }

      if (!user?.expiration_date) {
        setTimeUntilExpiry('');
        return;
      }

      const expiration = new Date(user.expiration_date);
      const now = new Date();
      const diff = expiration.getTime() - now.getTime();

      if (diff <= 0) {
        setTimeUntilExpiry('Expired');
        return;
      }

      const days = Math.floor(diff / (1000 * 60 * 60 * 24));
      const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
      const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
      const seconds = Math.floor((diff % (1000 * 60)) / 1000);

      if (days > 0) {
        setTimeUntilExpiry(`${days}d ${hours}h ${minutes}m`);
      } else if (hours > 0) {
        setTimeUntilExpiry(`${hours}h ${minutes}m ${seconds}s`);
      } else if (minutes > 0) {
        setTimeUntilExpiry(`${minutes}m ${seconds}s`);
      } else {
        setTimeUntilExpiry(`${seconds}s`);
      }
    };

    updateTimer();
    const interval = setInterval(updateTimer, 1000);

    return () => clearInterval(interval);
  }, [user?.expiration_date, user?.plan]);

  const fetchReleases = async () => {
    setReleasesLoading(true);

    try {
      const privateResponse = await releaseApi.getReleases();

      if (privateResponse.success && privateResponse.releases) {
        setReleases(privateResponse.releases);
        setError(null);
        console.log('[Dashboard] Loaded private releases:', privateResponse.releases.length);
        return;
      }

      const publicResponse = await releaseApi.getAll();

      if (publicResponse.success && publicResponse.releases) {
        setReleases(publicResponse.releases);
        setError(null);
        console.log('[Dashboard] Loaded public releases:', publicResponse.releases.length);
        return;
      }

      const errorMessage =
        privateResponse.message ||
        publicResponse.message ||
        'Failed to load releases';

      console.error('[Dashboard] Failed to fetch releases:', errorMessage);
      setError(errorMessage);
    } catch (error) {
      console.error('[Dashboard] Error fetching releases:', error);
      setError('Failed to load releases. Please refresh the page.');
    } finally {
      setReleasesLoading(false);
    }
  };


  const handleViewChangelog = (release: Release) => {
    setSelectedRelease(release);
    setIsChangelogModalOpen(true);
  };

  const latestRelease = releases[0] ?? null;
  const latestReleaseVersion = latestRelease?.version ?? (releasesLoading ? 'Loading...' : 'Unavailable');

  const handleDownload = async (release?: Release) => {
    console.log('[Dashboard] handleDownload called', { 
      hasActivePlan: hasActivePlan(), 
      userPlan: user?.plan, 
      release: release?.version || 'latest',
      releasesCount: releases.length 
    });

    if (!hasActivePlan()) {
      const errorMsg = `You need an active license to download releases. Current plan: ${user?.plan || 'none'}`;
      console.error('[Dashboard] Download blocked:', errorMsg);
      setError(errorMsg);
      return;
    }

    setIsDownloading(true);
    try {
      // Get latest release if none specified
      const releaseToDownload = release || releases[0];
      if (!releaseToDownload) {
        setError('No release available for download');
        return;
      }

      console.log('[Dashboard] Downloading release:', {
        version: releaseToDownload.version,
        download_url: releaseToDownload.download_url,
        file_url: releaseToDownload.file_url
      });

      // Get download URL - use download_url if available, otherwise construct from file_url
      const downloadPath = releaseToDownload.download_url || releaseToDownload.file_url;
      if (!downloadPath) {
        setError('No download URL available for this release');
        return;
      }

      // Extract filename from download_url
      // Format: /api/releases/download/{fileName}
      let fileName: string;
      if (downloadPath.includes('/api/releases/download/')) {
        // Extract from full path like "/api/releases/download/1.0_1764439962552_file.jar"
        fileName = downloadPath.split('/api/releases/download/')[1];
      } else if (downloadPath.startsWith('/')) {
        // If it's just a path, extract the last part
        fileName = downloadPath.split('/').pop() || '';
      } else {
        // If it's just a filename
        fileName = downloadPath;
      }
      
      // Clean up filename (remove any query params or fragments)
      fileName = fileName.split('?')[0].split('#')[0];
      
      if (!fileName || fileName === '') {
        setError('Invalid download URL format');
        return;
      }
      
      const token = getToken();
      if (!token) {
        setError('You must be logged in to download');
        return;
      }

      // Create download URL (no token in query string)
      const downloadUrl = `${API_BASE_URL}/api/releases/download/${encodeURIComponent(fileName)}`;
      
      // Use fetch to download the file with proper headers
      const response = await fetch(downloadUrl, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({ error: 'Download failed' }));
        const errorMessage = errorData.error || errorData.message || `Download failed: ${response.statusText}`;
        throw new Error(errorMessage);
      }

      // Get the blob from response
      const blob = await response.blob();
      
      if (!blob || blob.size === 0) {
        throw new Error('Downloaded file is empty');
      }
      
      // Create object URL and trigger download
      const blobUrl = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = blobUrl;
      link.download = releaseToDownload.file_name || fileName || 'EchoClient.jar';
      document.body.appendChild(link);
      link.click();
      
      // Cleanup
      setTimeout(() => {
        document.body.removeChild(link);
        window.URL.revokeObjectURL(blobUrl);
      }, 100);
      
      // Clear any previous errors
      setError(null);
    } catch (err: any) {
      setError(err.message || 'Failed to download release. Please try again.');
    } finally {
      setIsDownloading(false);
    }
  };

  const checkDiscordStatus = async () => {
    try {
      const response = await discordApi.getStatus();
      if (response.success) {
        setDiscordLinked(response.linked || false);
      }
    } catch (error) {
      console.error('Error checking Discord status:', error);
    }
  };

  const handleDiscordLink = async () => {
    try {
      const response = await discordApi.getAuthUrl();
      if (response.success && response.authUrl) {
        window.location.href = response.authUrl;
      }
    } catch (error) {
      console.error('Error getting Discord auth URL:', error);
    }
  };

  const handleDiscordUnlink = async () => {
    try {
      const response = await discordApi.unlink();
      if (response.success) {
        setDiscordLinked(false);
        await fetchUserData();
        await checkDiscordStatus();
      }
    } catch (error) {
      console.error('Error unlinking Discord:', error);
    }
  };

  const fetchUserData = async () => {
    const token = getToken();
    if (!token) {
      setError('Not authenticated');
      setLoading(false);
      if (onNavigate) {
        setTimeout(() => onNavigate('LOGIN'), 2000);
      }
      return;
    }

    try {
      const response = await authApi.getCurrentUser();
      
      if (response.success && response.user) {
        setUser(response.user);
        // Use HWID from API if available, otherwise show NOT SET
        if (response.user.hwid) {
          setHwid(response.user.hwid);
        } else {
          setHwid('NOT SET');
        }
        // Update Discord link status
        setDiscordLinked(!!response.user.discord_id);
      } else {
        const errorMsg = response.message || 'Failed to fetch user data';
        setError(errorMsg);
        console.error('Failed to fetch user:', errorMsg, response);
        if (onNavigate) {
          setTimeout(() => onNavigate('LOGIN'), 2000);
        }
      }
    } catch (err) {
      const errorMsg = 'Network error occurred';
      setError(errorMsg);
      console.error('Error fetching user:', err);
      if (onNavigate) {
        setTimeout(() => onNavigate('LOGIN'), 2000);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = async () => {
    await authApi.logout();
    if (onNavigate) {
      onNavigate('LANDING');
    } else {
      window.location.href = '/';
    }
  };

  const handleResetHWID = async () => {
    setResetError(null);
    setIsResetting(true);
    
    try {
      const response = await authApi.resetHWID();
      if (response.success) {
        setHwid('NOT SET');
        // Refresh user data
        await fetchUserData();
      } else {
        setResetError(response.message || 'Failed to reset HWID');
      }
    } catch (err) {
      setResetError('Network error occurred');
    } finally {
      setIsResetting(false);
    }
  };

  const handleChangePassword = async () => {
    setChangePasswordError(null);
    
    if (!newPassword || !confirmPassword) {
      setChangePasswordError('All fields are required');
      return;
    }

    if (newPassword.length < 6) {
      setChangePasswordError('Password must be at least 6 characters long');
      return;
    }

    if (newPassword !== confirmPassword) {
      setChangePasswordError('Passwords do not match');
      return;
    }

    setChangePasswordLoading(true);
    try {
      const response = await authApi.changePassword(newPassword);
      if (response.success) {
        setChangePasswordSuccess(true);
        setNewPassword('');
        setConfirmPassword('');
        setTimeout(() => {
          setShowChangePassword(false);
          setChangePasswordSuccess(false);
        }, 2000);
      } else {
        setChangePasswordError(response.message || 'Failed to change password');
      }
    } catch (err) {
      setChangePasswordError('Network error occurred');
    } finally {
      setChangePasswordLoading(false);
    }
  };

  const canResetHWID = () => {
    if (!user?.last_hwid_reset) return true;
    const daysSinceReset = (Date.now() - new Date(user.last_hwid_reset).getTime()) / (1000 * 60 * 60 * 24);
    return daysSinceReset >= 30;
  };

  const getDaysUntilReset = () => {
    if (!user?.last_hwid_reset) return 0;
    const daysSinceReset = (Date.now() - new Date(user.last_hwid_reset).getTime()) / (1000 * 60 * 60 * 24);
    return Math.max(0, Math.ceil(30 - daysSinceReset));
  };

  const isHwidSet = () => {
    return user?.hwid && hwid !== 'NOT SET';
  };

  const hasActivePlan = () => {
    if (!user) {
      console.log('[Dashboard] hasActivePlan: no user');
      return false;
    }

    if (user.stripe_subscription_status === 'paused') {
      console.log('[Dashboard] hasActivePlan: subscription is paused');
      return false;
    }

    if (typeof user.premium_access === 'boolean') {
      console.log('[Dashboard] hasActivePlan: using premium_access flag', {
        premium_access: user.premium_access
      });
      return user.premium_access;
    }
    
    const rawPlan = user.plan || '';
    const plan = String(rawPlan).trim().toLowerCase();

    console.log('[Dashboard] hasActivePlan check:', {
      rawPlan,
      plan,
      expiration_date: user.expiration_date,
      hasExpiration: !!user.expiration_date
    });

    if (plan === '' || plan === 'free' || plan === 'n/a' || plan === 'none') {
      console.log('[Dashboard] hasActivePlan: invalid plan type');
      return false;
    }

    // Lifetime plans are always active
    if (plan === 'lifetime') {
      console.log('[Dashboard] hasActivePlan: lifetime plan detected - always active');
      return true;
    }

    // For premium and other plans, check expiration date
    if (user.expiration_date) {
      const expiration = new Date(user.expiration_date);
      const now = new Date();
      const isActive = expiration > now;
      console.log('[Dashboard] hasActivePlan: checking expiration', {
        expiration: expiration.toISOString(),
        now: now.toISOString(),
        isActive
      });
      return isActive;
    }

    // If plan is set but no expiration, assume it's active (for legacy accounts)
    console.log('[Dashboard] hasActivePlan: plan set but no expiration - assuming active');
    return true;
  };

  const getPlanStatusText = () => {
    if (!user) {
      return 'Free';
    }

    if (user.stripe_subscription_status === 'paused') {
      return 'Paused';
    }

    if (user.stripe_collection_paused) {
      return 'Collection Paused';
    }

    if (!hasActivePlan() && user.plan && user.plan !== 'free') {
      return 'Expired';
    }

    return user.plan?.toUpperCase() || 'Free';
  };

  const getPlanStatusClassName = () => {
    if (user?.stripe_subscription_status === 'paused') {
      return 'text-yellow-400';
    }

    if (user?.stripe_collection_paused) {
      return 'text-orange-400';
    }

    return hasActivePlan() ? 'text-echo-primary' : 'text-echo-muted';
  };

  const getSubscriptionHintText = () => {
    if (!user?.stripe_subscription_id) {
      return null;
    }

    if (user.stripe_subscription_status === 'paused') {
      return 'Subscription is paused';
    }

    if (user.stripe_collection_paused) {
      return 'Collection is paused';
    }

    return hasActivePlan() ? 'Click to manage subscription' : 'Click to reactivate subscription';
  };

  const formatDate = (dateString?: string) => {
    if (!dateString) return 'N/A';
    try {
      const date = new Date(dateString);
      return date.toLocaleDateString('en-US', { 
        year: 'numeric', 
        month: 'short', 
        day: 'numeric' 
      });
    } catch {
      return 'N/A';
    }
  };

  const formatUserId = (id?: string) => {
    if (!id) return 'N/A';
    return `user_${id.substring(0, 6)}`;
  };

  const formatUID = (userNumber?: number) => {
    if (!userNumber) return '#0001';
    if (userNumber == -1) return 'N/A';
    return `#${userNumber.toString().padStart(4, '0')}`;
  };

  if (loading) {
    return (
      <div className="min-h-screen pt-24 pb-12 px-4 sm:px-6 lg:px-8 bg-echo-bg flex items-center justify-center">
        <div className="text-center">
          <Loader2 className="w-8 h-8 text-echo-primary animate-spin mx-auto mb-4" />
          <p className="font-sans text-echo-muted text-xs">Loading dashboard...</p>
        </div>
      </div>
    );
  }

  if (error && !user) {
    return (
      <div className="min-h-screen pt-24 pb-12 px-4 sm:px-6 lg:px-8 bg-echo-bg flex items-center justify-center">
        <Card className="p-8 text-center max-w-md">
          <p className="font-sans text-red-400 text-sm mb-4">{error}</p>
          <p className="font-sans text-echo-muted text-xs mb-4">Redirecting to login...</p>
          <Button 
            variant="outline" 
            size="sm"
            onClick={() => onNavigate?.('LOGIN')}
          >
            Go to Login
          </Button>
        </Card>
      </div>
    );
  }

  // Don't render dashboard if no user data
  if (!user) {
    return (
      <div className="min-h-screen pt-24 pb-12 px-4 sm:px-6 lg:px-8 bg-echo-bg flex items-center justify-center">
        <div className="text-center">
          <Loader2 className="w-8 h-8 text-echo-primary animate-spin mx-auto mb-4" />
          <p className="font-sans text-echo-muted text-xs">Loading user data...</p>
        </div>
      </div>
    );
  }

  const handleRedeemSuccess = async () => {
    // Refresh user data after successful redemption
    await fetchUserData();
  };

  const handlePaymentSuccess = async () => {
    // Refresh user data after successful payment
    await fetchUserData();
  };

  return (
    <div className="min-h-screen pt-24 pb-12 px-4 sm:px-6 lg:px-8 bg-echo-bg">
      <KeyModal 
        isOpen={isKeyModalOpen} 
        onClose={() => setIsKeyModalOpen(false)}
        onRedeemSuccess={handleRedeemSuccess}
      />
      <PaymentModal
        isOpen={isPaymentModalOpen}
        onClose={() => setIsPaymentModalOpen(false)}
        onPaymentSuccess={handlePaymentSuccess}
        hasSubscription={!!user?.stripe_subscription_id}
      />
      <ChangelogModal
        isOpen={isChangelogModalOpen}
        onClose={() => {
          setIsChangelogModalOpen(false);
          setSelectedRelease(null);
        }}
        release={selectedRelease}
      />
      
      {discordSuccessMessage && (
        <div className="max-w-7xl mx-auto mb-4">
          <div className="bg-echo-primary/10 border border-echo-primary/50 p-4 rounded-sm flex items-center justify-between">
            <div className="flex items-center space-x-3">
              <i className="fa-brands fa-discord text-echo-primary text-lg"></i>
              <p className="font-sans text-sm text-echo-primary font-bold">{discordSuccessMessage}</p>
            </div>
            <button
              onClick={() => setDiscordSuccessMessage(null)}
              className="text-echo-primary hover:text-echo-white transition-colors"
            >
              <span className="text-xl">&times;</span>
            </button>
          </div>
        </div>
      )}

      <div className="max-w-7xl mx-auto space-y-4">
        
        <div className="flex flex-col md:flex-row md:items-end justify-between mb-8">
          <div>
            <h1 className="text-3xl font-bold text-white tracking-tight mb-2">Dashboard</h1>
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-echo-primary to-blue-400 flex items-center justify-center text-black font-bold text-xs">
                {user?.username?.substring(0, 2).toUpperCase()}
              </div>
              <div>
                <p className="text-sm font-medium text-white">
                  {user?.username || 'Agent'}
                </p>
                <p className="text-xs text-echo-muted">
                  {hasActivePlan() ? (user?.plan?.toLowerCase() === 'lifetime' ? 'Lifetime Member' : 'Premium Member') : 'Free Plan'}
                </p>
              </div>
            </div>
          </div>
          <div className="mt-4 md:mt-0 flex items-center gap-3">
             {!discordLinked ? (
               <Button
                 variant="secondary"
                 size="sm"
                 onClick={handleDiscordLink}
                 className="rounded-full"
               >
                 <i className="fa-brands fa-discord mr-2 text-xs"></i>
                 Link Discord
               </Button>
             ) : (
               <Button
                 variant="outline"
                 size="sm"
                 onClick={handleDiscordUnlink}
                 className="rounded-full"
               >
                 <i className="fa-brands fa-discord mr-2 text-xs"></i>
                 Unlink Discord
               </Button>
             )}
             {!hasActivePlan() && (
               <>
                 <Button 
                   variant="outline" 
                   size="sm" 
                   onClick={() => setIsKeyModalOpen(true)}
                   className="rounded-full"
                 >
                   <Key className="w-3 h-3 mr-2" />
                   Redeem Key
                 </Button>
                 <Button 
                   variant="primary" 
                   size="sm" 
                   onClick={() => setIsPaymentModalOpen(true)}
                   className="rounded-full"
                 >
                   <CreditCard className="w-3 h-3 mr-2" />
                   Buy Now
                 </Button>
               </>
             )}
              <Button 
                variant="ghost" 
                size="sm" 
                onClick={handleLogout}
                className="rounded-full"
              >
                <LogOut className="w-3 h-3 mr-2" />
                Sign Out
              </Button>
          </div>
        </div>

        {error && (
          <Card className="p-4 bg-red-500/10 border-red-500/30 mb-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-3">
                <Info className="w-5 h-5 text-red-500" />
                <div>
                  <p className="font-sans text-sm text-white font-bold mb-1">Error</p>
                  <p className="font-sans text-xs text-red-400">{error}</p>
                </div>
              </div>
              <button
                onClick={() => setError(null)}
                className="text-red-400 hover:text-red-300 transition-colors"
              >
                <span className="text-xl">&times;</span>
              </button>
            </div>
          </Card>
        )}

        {!hasActivePlan() && (
          <Card className="p-4 bg-yellow-500/10 border-yellow-500/30 mb-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-3">
                <Key className="w-5 h-5 text-yellow-500" />
                <div>
                  <p className="font-sans text-sm text-white font-bold mb-1">Upgrade Your Plan</p>
                  <p className="font-sans text-xs text-echo-muted">Purchase a plan or redeem a license key to unlock premium features</p>
                </div>
              </div>
              <div className="flex gap-2">
                <Button 
                  variant="primary" 
                  size="sm"
                  onClick={() => setIsPaymentModalOpen(true)}
                  className="h-9 text-xs"
                >
                  <CreditCard className="w-4 h-4 mr-2" />
                  BUY PLAN
                </Button>
                <Button 
                  variant="outline" 
                  size="sm"
                  onClick={() => setIsKeyModalOpen(true)}
                  className="h-9 text-xs"
                >
                  <Key className="w-4 h-4 mr-2" />
                  REDEEM KEY
                </Button>
              </div>
            </div>
          </Card>
        )}

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
          <Card className="p-4" hoverEffect>
             <div className="flex items-center justify-between">
               <div>
                  <p className="text-xs font-medium text-echo-muted mb-1">User ID</p>
                  <p className="text-sm font-sans font-semibold text-white truncate max-w-[100px]" title={formatUserId(user?.id)}>
                    {formatUserId(user?.id)}
                  </p>
               </div>
               <div className="w-8 h-8 rounded-full bg-white/5 flex items-center justify-center">
                  <User className="w-4 h-4 text-echo-primary" />
               </div>
             </div>
          </Card>
          
          <Card className="p-4" hoverEffect>
             <div className="flex items-center justify-between">
               <div>
                  <p className="text-xs font-medium text-echo-muted mb-1">UID</p>
                  <p className="text-sm font-sans font-semibold text-white">{formatUID(user?.userNumber)}</p>
               </div>
               <div className="w-8 h-8 rounded-full bg-white/5 flex items-center justify-center">
                  <Hash className="w-4 h-4 text-echo-primary" />
               </div>
             </div>
          </Card>

          <Card className={`p-4 ${user?.stripe_subscription_id || !hasActivePlan() ? 'cursor-pointer' : ''}`} hoverEffect onClick={() => {
            if (user?.stripe_subscription_id) {
              setIsPaymentModalOpen(true);
            } else if (!hasActivePlan()) {
              setIsKeyModalOpen(true);
            }
            // If user has active plan but no stripe_subscription_id (lifetime), do nothing
          }}>
             <div className="flex items-center justify-between">
               <div>
                  <p className="text-xs font-medium text-echo-muted mb-1">Plan Status</p>
                  <div className={`text-sm font-semibold flex items-center gap-1 ${getPlanStatusClassName()}`}>
                    {getPlanStatusText()}
                  </div>
                  {hasActivePlan() && timeUntilExpiry && user?.expiration_date && user?.plan?.toLowerCase() !== 'lifetime' && (
                    <p className="text-[10px] text-echo-muted mt-1 font-sans">
                      {timeUntilExpiry}
                    </p>
                  )}
                  {getSubscriptionHintText() && (
                    <p className="text-[10px] text-echo-primary mt-1">
                      {getSubscriptionHintText()}
                    </p>
                  )}
               </div>
               <div className="w-8 h-8 rounded-full bg-white/5 flex items-center justify-center">
                  <Shield className={`w-4 h-4 ${getPlanStatusClassName()}`} />
               </div>
             </div>
          </Card>

          <Card className="p-4" hoverEffect>
             <div className="flex items-center justify-between">
               <div>
                  <p className="text-xs font-medium text-echo-muted mb-1">Member Since</p>
                  <p className="text-sm font-sans font-semibold text-white">{formatDate(user?.createdAt)}</p>
               </div>
               <div className="w-8 h-8 rounded-full bg-white/5 flex items-center justify-center">
                  <Calendar className="w-4 h-4 text-echo-primary" />
               </div>
             </div>
          </Card>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          
          <div className="lg:col-span-4 flex flex-col gap-6">
            <Card className="p-6 relative overflow-hidden flex flex-col">
               <div className="flex justify-between items-start mb-6">
                 <div>
                   <h2 className="text-lg font-bold text-white mb-1">Latest Build</h2>
                    <p className="text-xs text-echo-muted">Version {latestReleaseVersion}</p>
                  </div>
                 <span className="px-3 py-1 bg-echo-primary/10 border border-echo-primary/20 text-echo-primary text-xs font-semibold rounded-full flex items-center">
                   <div className="w-1.5 h-1.5 bg-echo-primary rounded-full mr-2"></div>
                   Beta
                 </span>
               </div>

               <div className="space-y-4 mb-6">
                 <div className="flex justify-between items-center py-2 border-b border-white/5">
                    <span className="text-xs font-medium text-echo-muted">Target Version</span>
                    <span className="text-sm font-medium text-white">Minecraft 1.21.11</span>
                 </div>
                 <div className="flex justify-between items-center py-2 border-b border-white/5">
                    <span className="text-xs font-medium text-echo-muted">Loader</span>
                    <span className="text-sm font-medium text-white">Fabric</span>
                 </div>
               </div>

               <div className="mb-6">
                   <div className="text-xs font-medium text-echo-muted mb-3 flex items-center">
                      Required Dependencies
                   </div>
                   <a
                     href="https://modrinth.com/mod/fabric-api"
                     target="_blank"
                     rel="noopener noreferrer"
                     className="bg-white/5 border border-white/5 px-4 py-3 flex justify-between items-center group cursor-pointer hover:bg-white/10 transition-colors rounded-xl"
                   >
                      <div className="flex items-center">
                         <Box className="w-4 h-4 text-echo-primary mr-3" />
                         <span className="text-sm font-medium text-white">Fabric-API</span>
                      </div>
                      <ExternalLink className="w-3 h-3 text-echo-muted group-hover:text-white" />
                   </a>
               </div>

                <Button
                  fullWidth
                  size="lg"
                  className="rounded-xl shadow-lg shadow-echo-primary/20"
                  onClick={() => handleDownload()}
                  disabled={!hasActivePlan() || releases.length === 0 || isDownloading}
                >
                 {isDownloading ? (
                   <>
                     <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                     Downloading...
                   </>
                 ) : (
                   <>
                     <Download className="w-4 h-4 mr-2" />
                     Download Client
                   </>
                 )}
               </Button>
            </Card>

            {/* Security card (HWID + Password tab) */}
            <Card className="p-6">
               <div className="mb-4 flex justify-between items-center">
                  <div className="flex items-center space-x-2">
                     <Shield className="w-4 h-4 text-echo-muted" />
                     <h3 className="text-sm font-bold text-white">Security</h3>
                  </div>
                  <div className="flex items-center gap-2 bg-white/5 rounded-lg p-1">
                     <button
                       className={`px-3 py-1 rounded-md text-xs font-medium ${securityTab === 'HWID' ? 'bg-white text-black shadow-md' : 'text-echo-muted hover:text-white'}`}
                       onClick={() => setSecurityTab('HWID')}
                     >
                       HWID
                     </button>
                     <button
                       className={`px-3 py-1 rounded-md text-xs font-medium ${securityTab === 'PASSWORD' ? 'bg-white text-black shadow-md' : 'text-echo-muted hover:text-white'}`}
                       onClick={() => setSecurityTab('PASSWORD')}
                     >
                       Password
                     </button>
                  </div>
               </div>

               {securityTab === 'HWID' ? (
                 <div>
                   <div className="mb-4">
                     <div className="text-xs font-medium text-echo-muted mb-3 flex items-center">Hardware ID</div>
                     <div className="w-full bg-black/40 border border-white/5 p-3 font-mono text-xs text-echo-muted rounded-xl truncate mb-4 text-center">
                       {hwid}
                     </div>
                     <Button 
                       fullWidth 
                       variant="outline" 
                       size="sm" 
                       className="rounded-xl hover:border-red-500/50 hover:text-red-400"
                       onClick={handleResetHWID}
                       disabled={!canResetHWID() || isResetting || !isHwidSet()}
                     >
                        <RefreshCw className={`w-3 h-3 mr-2 ${isResetting ? 'animate-spin' : ''}`} />
                        {isResetting ? 'Resetting...' : 'Reset HWID'}
                     </Button>
                     {!canResetHWID() && (
                       <p className="mt-3 text-[10px] text-echo-muted text-center">Next reset available in {getDaysUntilReset()} days</p>
                     )}
                   </div>
                 </div>
               ) : (
                 <div className="space-y-4">
                   {changePasswordSuccess && (
                     <div className="p-3 bg-green-500/10 border border-green-500/20 rounded-xl text-green-400 text-sm text-center">Password changed successfully!</div>
                   )}
                   {changePasswordError && (
                     <div className="p-3 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-sm">{changePasswordError}</div>
                   )}
                   <Input
                     type="password"
                     label=""
                     placeholder="New Password"
                     icon={<Lock className="w-4 h-4 text-echo-muted" />}
                     value={newPassword}
                     onChange={(e) => setNewPassword(e.target.value)}
                     disabled={changePasswordLoading}
                   />
                   <Input
                     type="password"
                     label=""
                     placeholder="Confirm New Password"
                     icon={<Lock className="w-4 h-4 text-echo-muted" />}
                     value={confirmPassword}
                     onChange={(e) => setConfirmPassword(e.target.value)}
                     disabled={changePasswordLoading}
                   />
                   <Button
                     fullWidth
                     variant="primary"
                     size="sm"
                     className="rounded-xl"
                     onClick={handleChangePassword}
                     disabled={changePasswordLoading}
                   >
                     {changePasswordLoading ? 'Changing...' : 'Change Password'}
                   </Button>
                 </div>
               )}
            </Card>
            
          </div>

          <div className="lg:col-span-8">
            <Card
              className="p-6 flex flex-col md:h-[382px]"
              hoverEffect
            >
               
               <div className="flex flex-col md:overflow-hidden">
               <div className="flex-1">
               <div className="mb-8">
                  <h3 className="text-lg font-bold text-white mb-2">Installation Guide</h3>
                  <p className="text-echo-muted text-sm">Follow these steps to install Echo Client into your game.</p>
               </div>

               <div className="flex p-1 bg-white/5 rounded-xl mb-8 w-fit">
                 {['MC_LAUNCHER', 'MODRINTH', 'FEATHER', 'PRISM'].map((launcher) => (
                   <button
                     key={launcher}
                     onClick={() => setSelectedLauncher(launcher as any)}
                     className={`px-4 py-2 rounded-lg text-xs font-medium transition-all ${
                       selectedLauncher === launcher 
                         ? 'bg-white text-black shadow-md' 
                         : 'text-echo-muted hover:text-white'
                     }`}
                   >
                     {launcher === 'MC_LAUNCHER' ? 'Minecraft' : 
                      launcher.charAt(0) + launcher.slice(1).toLowerCase()}
                   </button>
                 ))}
               </div>

               <div className="grid grid-cols-1 md:grid-cols-3 gap-8 relative pb-4">
                  {/* Connecting Line */}
                  <div className="hidden md:block absolute top-6 left-[16%] right-[16%] h-0.5 bg-gradient-to-r from-transparent via-white/10 to-transparent pointer-events-none"></div>

                  {selectedLauncher === 'MC_LAUNCHER' && (
                    <>
                      <div className="relative z-10 flex flex-col items-center text-center group">
                         <div className="w-12 h-12 bg-black border border-white/10 rounded-2xl flex items-center justify-center mb-4 shadow-xl group-hover:border-echo-primary/50 transition-colors">
                            <Download className="w-5 h-5 text-white" />
                         </div>
                         <span className="text-xs font-bold text-echo-primary mb-1 uppercase tracking-wide">Step 01</span>
                         <h4 className="text-sm font-bold text-white mb-2">Install Fabric</h4>
                         <p className="text-xs text-echo-muted leading-relaxed max-w-[200px]">
                            Download and install <span className="text-white">Fabric 1.21.11</span> from the official website for your Minecraft Launcher.
                         </p>
                      </div>

                      <div className="relative z-10 flex flex-col items-center text-center group">
                         <div className="w-12 h-12 bg-black border border-white/10 rounded-2xl flex items-center justify-center mb-4 shadow-xl group-hover:border-echo-primary/50 transition-colors">
                            <Folder className="w-5 h-5 text-white" />
                         </div>
                         <span className="text-xs font-bold text-echo-primary mb-1 uppercase tracking-wide">Step 02</span>
                         <h4 className="text-sm font-bold text-white mb-2">Add Mods</h4>
                         <p className="text-xs text-echo-muted leading-relaxed max-w-[200px]">
                            Place <span className="text-white">fabric-api</span> and <span className="text-white">Echo Client</span> in your <span className="text-white">.minecraft/mods</span> folder.
                         </p>
                      </div>

                      <div className="relative z-10 flex flex-col items-center text-center group">
                         <div className="w-12 h-12 bg-black border border-white/10 rounded-2xl flex items-center justify-center mb-4 shadow-xl group-hover:border-echo-primary/50 transition-colors">
                            <Play className="w-5 h-5 text-white ml-0.5" />
                         </div>
                         <span className="text-xs font-bold text-echo-primary mb-1 uppercase tracking-wide">Step 03</span>
                         <h4 className="text-sm font-bold text-white mb-2">Launch Game</h4>
                         <p className="text-xs text-echo-muted leading-relaxed max-w-[200px]">
                            Select the Fabric profile and launch. Press <span className="px-1.5 py-0.5 bg-white/10 rounded text-xs font-mono">RSHIFT</span> to open Echo menu.
                         </p>
                      </div>
                    </>
                  )}

                  {selectedLauncher === 'MODRINTH' && (
                    <>
                      <div className="relative z-10 flex flex-col items-center text-center group">
                         <div className="w-12 h-12 bg-black border border-white/10 rounded-2xl flex items-center justify-center mb-4 shadow-xl group-hover:border-echo-primary/50 transition-colors">
                            <Download className="w-5 h-5 text-white" />
                         </div>
                         <span className="text-xs font-bold text-echo-primary mb-1 uppercase tracking-wide">Step 01</span>
                         <h4 className="text-sm font-bold text-white mb-2">Create Profile</h4>
                         <p className="text-xs text-echo-muted leading-relaxed max-w-[200px]">
                            Create a new profile in Modrinth App and select <span className="text-white">Fabric 1.21.11</span> as the loader.
                         </p>
                      </div>

                      <div className="relative z-10 flex flex-col items-center text-center group">
                         <div className="w-12 h-12 bg-black border border-white/10 rounded-2xl flex items-center justify-center mb-4 shadow-xl group-hover:border-echo-primary/50 transition-colors">
                            <Folder className="w-5 h-5 text-white" />
                         </div>
                         <span className="text-xs font-bold text-echo-primary mb-1 uppercase tracking-wide">Step 02</span>
                         <h4 className="text-sm font-bold text-white mb-2">Install Mods</h4>
                         <p className="text-xs text-echo-muted leading-relaxed max-w-[200px]">
                            Use Modrinth's mod manager to install <span className="text-white">Fabric API</span>, then manually add <span className="text-white">Echo Client</span>.
                         </p>
                      </div>

                      <div className="relative z-10 flex flex-col items-center text-center group">
                         <div className="w-12 h-12 bg-black border border-white/10 rounded-2xl flex items-center justify-center mb-4 shadow-xl group-hover:border-echo-primary/50 transition-colors">
                            <Play className="w-5 h-5 text-white ml-0.5" />
                         </div>
                         <span className="text-xs font-bold text-echo-primary mb-1 uppercase tracking-wide">Step 03</span>
                         <h4 className="text-sm font-bold text-white mb-2">Launch Game</h4>
                         <p className="text-xs text-echo-muted leading-relaxed max-w-[200px]">
                            Launch through Modrinth App. Press <span className="px-1.5 py-0.5 bg-white/10 rounded text-xs font-mono">RSHIFT</span> to open Echo menu.
                         </p>
                      </div>
                    </>
                  )}

                  {selectedLauncher === 'FEATHER' && (
                    <>
                      <div className="relative z-10 flex flex-col items-center text-center group">
                         <div className="w-12 h-12 bg-black border border-white/10 rounded-2xl flex items-center justify-center mb-4 shadow-xl group-hover:border-echo-primary/50 transition-colors">
                            <Download className="w-5 h-5 text-white" />
                         </div>
                         <span className="text-xs font-bold text-echo-primary mb-1 uppercase tracking-wide">Step 01</span>
                         <h4 className="text-sm font-bold text-white mb-2">Setup Profile</h4>
                         <p className="text-xs text-echo-muted leading-relaxed max-w-[200px]">
                            Create a new instance in Feather and select <span className="text-white">Fabric 1.21.11</span> as the mod loader.
                         </p>
                      </div>

                      <div className="relative z-10 flex flex-col items-center text-center group">
                         <div className="w-12 h-12 bg-black border border-white/10 rounded-2xl flex items-center justify-center mb-4 shadow-xl group-hover:border-echo-primary/50 transition-colors">
                            <Folder className="w-5 h-5 text-white" />
                         </div>
                         <span className="text-xs font-bold text-echo-primary mb-1 uppercase tracking-wide">Step 02</span>
                         <h4 className="text-sm font-bold text-white mb-2">Add Mods</h4>
                         <p className="text-xs text-echo-muted leading-relaxed max-w-[200px]">
                            Drop <span className="text-white">fabric-api</span> and <span className="text-white">Echo Client</span> into the instance's mods folder.
                         </p>
                      </div>

                      <div className="relative z-10 flex flex-col items-center text-center group">
                         <div className="w-12 h-12 bg-black border border-white/10 rounded-2xl flex items-center justify-center mb-4 shadow-xl group-hover:border-echo-primary/50 transition-colors">
                            <Play className="w-5 h-5 text-white ml-0.5" />
                         </div>
                         <span className="text-xs font-bold text-echo-primary mb-1 uppercase tracking-wide">Step 03</span>
                         <h4 className="text-sm font-bold text-white mb-2">Launch Game</h4>
                         <p className="text-xs text-echo-muted leading-relaxed max-w-[200px]">
                            Launch the instance from Feather. Press <span className="px-1.5 py-0.5 bg-white/10 rounded text-xs font-mono">RSHIFT</span> to open Echo menu.
                         </p>
                      </div>
                    </>
                  )}

                  {selectedLauncher === 'PRISM' && (
                    <>
                      <div className="relative z-10 flex flex-col items-center text-center group">
                         <div className="w-12 h-12 bg-black border border-white/10 rounded-2xl flex items-center justify-center mb-4 shadow-xl group-hover:border-echo-primary/50 transition-colors">
                            <Download className="w-5 h-5 text-white" />
                         </div>
                         <span className="text-xs font-bold text-echo-primary mb-1 uppercase tracking-wide">Step 01</span>
                         <h4 className="text-sm font-bold text-white mb-2">Create Instance</h4>
                         <p className="text-xs text-echo-muted leading-relaxed max-w-[200px]">
                            Create a new instance in Prism Launcher and install <span className="text-white">Fabric 1.21.11</span> loader.
                         </p>
                      </div>

                      <div className="relative z-10 flex flex-col items-center text-center group">
                         <div className="w-12 h-12 bg-black border border-white/10 rounded-2xl flex items-center justify-center mb-4 shadow-xl group-hover:border-echo-primary/50 transition-colors">
                            <Folder className="w-5 h-5 text-white" />
                         </div>
                         <span className="text-xs font-bold text-echo-primary mb-1 uppercase tracking-wide">Step 02</span>
                         <h4 className="text-sm font-bold text-white mb-2">Install Mods</h4>
                         <p className="text-xs text-echo-muted leading-relaxed max-w-[200px]">
                            Add <span className="text-white">fabric-api</span> and <span className="text-white">Echo Client</span> to the instance's mods folder.
                         </p>
                      </div>

                      <div className="relative z-10 flex flex-col items-center text-center group">
                         <div className="w-12 h-12 bg-black border border-white/10 rounded-2xl flex items-center justify-center mb-4 shadow-xl group-hover:border-echo-primary/50 transition-colors">
                            <Play className="w-5 h-5 text-white ml-0.5" />
                         </div>
                         <span className="text-xs font-bold text-echo-primary mb-1 uppercase tracking-wide">Step 03</span>
                         <h4 className="text-sm font-bold text-white mb-2">Launch Game</h4>
                         <p className="text-xs text-echo-muted leading-relaxed max-w-[200px]">
                            Launch the instance from Prism. Press <span className="px-1.5 py-0.5 bg-white/10 rounded text-xs font-mono">RSHIFT</span> to open Echo menu.
                         </p>
                      </div>
                    </>
                  )}
               </div>

               </div>
               </div>
            </Card>

            {/* Releases Section */}
            <Card className="p-0 mt-6">
              <div className="p-6 max-h-[232px] overflow-y-auto">
              <div className="mb-6 flex items-center justify-between">
                <div>
                  <h3 className="text-lg font-bold text-white mb-1">Release History</h3>
                  <p className="text-xs text-echo-muted">Track updates and changes</p>
                </div>
                {releasesLoading && (
                  <Loader2 className="w-5 h-5 text-echo-muted animate-spin" />
                )}
              </div>

              {releasesLoading && releases.length === 0 ? (
                <div className="text-center py-12">
                  <Loader2 className="w-8 h-8 text-echo-primary animate-spin mx-auto mb-3" />
                  <p className="text-echo-muted text-sm">Fetching release data...</p>
                </div>
              ) : releases.length === 0 ? (
                <div className="text-center py-12 bg-white/[0.02] rounded-xl border border-dashed border-white/10">
                  <p className="text-echo-muted text-sm">No releases available</p>
                </div>
              ) : (
                <div className="space-y-4">
                  {releases.map((release, idx) => {
                    const fileSizeMB = release.file_size ? (release.file_size / (1024 * 1024)).toFixed(2) : 'N/A';
                    
                    return (
                      <div
                        key={idx}
                        className="group flex items-center justify-between p-4 rounded-2xl bg-white/[0.02] hover:bg-white/[0.04] transition-all border border-transparent hover:border-white/5 cursor-pointer"
                        onClick={() => handleViewChangelog(release)}
                      >
                        <div className="flex items-center gap-4">
                           <div className="w-10 h-10 rounded-full bg-white/5 flex items-center justify-center group-hover:scale-110 transition-transform">
                              <Box className="w-5 h-5 text-echo-primary" />
                           </div>
                           <div>
                              <div className="flex items-center gap-2 mb-1">
                                 <h4 className="text-sm font-bold text-white">{release.version}</h4>
                                 {idx === 0 && <span className="text-[10px] font-bold bg-echo-primary/20 text-echo-primary px-2 py-0.5 rounded-full">New</span>}
                              </div>
                              <p className="text-xs text-echo-muted font-medium">
                                 Released on {formatDate(release.date)}
                              </p>
                           </div>
                        </div>

                        <div className="flex items-center gap-6">
                           <div className="hidden sm:block text-right">
                              <p className="text-xs text-echo-muted mb-0.5">Size</p>
                              <p className="text-sm font-medium text-white">{fileSizeMB} MB</p>
                           </div>
                           <div className="flex items-center gap-2">
                              {/* Only show download button for the latest release (idx === 0) and if user has active plan */}
                              {idx === 0 && hasActivePlan() && (
                                <Button
                                  variant="primary"
                                  size="sm"
                                  className="rounded-full"
                                  disabled={isDownloading}
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    handleDownload(release);
                                  }}
                                >
                                  {isDownloading ? (
                                    <Loader2 className="w-4 h-4 animate-spin" />
                                  ) : (
                                    <Download className="w-4 h-4" />
                                  )}
                                </Button>
                              )}
                           </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
              </div>
            </Card>
          </div>
        </div>
      </div>
    </div>
  );
};
