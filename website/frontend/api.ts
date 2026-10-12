// API Configuration
// Default to localhost:3001 if VITE_API_URL is not set
export const API_BASE_URL = typeof import.meta !== 'undefined' && (import.meta as any).env?.VITE_API_URL 
  ? (import.meta as any).env.VITE_API_URL 
  : 'https://api.echoclient.net';

// Types
export interface ApiResponse<T = any> {
  success: boolean;
  message?: string;
  data?: T;
  [key: string]: any;
}

export interface LoginRequest {
  username: string;
  password: string;
  hwid?: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  turnstileToken?: string;
}

export interface User {
  id: string;
  username: string;
  hwid?: string;
  userNumber?: number;
  plan?: string;
  expiration_date?: string;
  premium_access?: boolean;
  admin?: boolean;
  discord_id?: string;
  discord_username?: string;
  last_hwid_reset?: string;
  last_login?: string;
  createdAt?: string;
  stripe_customer_id?: string;
  stripe_subscription_id?: string;
  stripe_subscription_status?: string;
  stripe_collection_paused?: boolean;
}

export interface LoginResponse {
  success: boolean;
  message: string;
  user: User;
  sessionToken: string;
  timestamp: number;
}

export interface RegisterResponse {
  success: boolean;
  message: string;
  user: User;
}

// Token management
const TOKEN_KEY = 'echo_session_token';

export const getToken = (): string | null => {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
};

export const setToken = (token: string): void => {
  if (typeof window === 'undefined') return;
  localStorage.setItem(TOKEN_KEY, token);
};

export const removeToken = (): void => {
  if (typeof window === 'undefined') return;
  localStorage.removeItem(TOKEN_KEY);
};

// API helper function
async function apiRequest<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<ApiResponse<T>> {
  const token = getToken();
  const url = `${API_BASE_URL}${endpoint}`;

  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    ...options.headers,
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  try {
    const response = await fetch(url, {
      ...options,
      headers,
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        message: data.message || `HTTP error! status: ${response.status}`,
        ...data,
      };
    }

    return {
      success: true,
      ...data,
    };
  } catch (error) {
    return {
      success: false,
      message: error instanceof Error ? error.message : 'Network error occurred',
    };
  }
}

// Auth API functions
export const authApi = {
  /**
   * Register a new user account
   */
  register: async (data: RegisterRequest): Promise<ApiResponse<RegisterResponse>> => {
    return apiRequest<RegisterResponse>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  /**
   * Login with username and password
   */
  login: async (data: LoginRequest): Promise<ApiResponse<LoginResponse>> => {
    const response = await apiRequest<LoginResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(data),
    });

    // Store token if login successful
    if (response.success && response.sessionToken) {
      setToken(response.sessionToken);
    }

    return response;
  },

  /**
   * Logout (clears token on client side)
   */
  logout: async (): Promise<ApiResponse> => {
    const response = await apiRequest('/api/auth/logout', {
      method: 'POST',
    });

    // Remove token regardless of response
    removeToken();

    return response;
  },

  /**
   * Get current user information
   */
  getCurrentUser: async (): Promise<ApiResponse<{ user: User }>> => {
    return apiRequest<{ user: User }>('/api/auth/me');
  },

  /**
   * Reset HWID (once per month)
   */
  resetHWID: async (): Promise<ApiResponse<{ daysRemaining?: number }>> => {
    return apiRequest<{ daysRemaining?: number }>('/api/auth/reset-hwid', {
      method: 'POST',
    });
  },

  /**
   * Reset password
   */
  resetPassword: async (data: { username?: string; newPassword: string }): Promise<ApiResponse> => {
    return apiRequest('/api/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  /**
   * Change password (for authenticated users)
   */
  changePassword: async (newPassword: string): Promise<ApiResponse> => {
    return apiRequest('/api/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({ newPassword }),
    });
  },
};

// Discord API functions
export const discordApi = {
  /**
   * Get Discord OAuth authorization URL (for linking account)
   */
  getAuthUrl: async (): Promise<ApiResponse<{ authUrl: string }>> => {
    return apiRequest<{ authUrl: string }>('/api/auth/discord/auth-url');
  },

  /**
   * Get Discord login URL (for login without existing account)
   */
  getLoginUrl: async (): Promise<ApiResponse<{ authUrl: string }>> => {
    return apiRequest<{ authUrl: string }>('/api/auth/discord/login-url');
  },

  /**
   * Get Discord link status
   */
  getStatus: async (): Promise<ApiResponse<{ linked: boolean; discord_id?: string; discord_username?: string }>> => {
    return apiRequest<{ linked: boolean; discord_id?: string; discord_username?: string }>('/api/auth/discord/status');
  },

  /**
   * Exchange authorization code for session token
   */
  exchangeCode: async (code: string): Promise<ApiResponse<{ sessionToken: string }>> => {
    return apiRequest<{ sessionToken: string }>('/api/auth/discord/exchange-code', {
      method: 'POST',
      body: JSON.stringify({ code }),
    });
  },

  /**
   * Unlink Discord account
   */
  unlink: async (): Promise<ApiResponse> => {
    return apiRequest('/api/auth/discord/unlink', {
      method: 'POST',
    });
  },
};

// Health check
export const healthCheck = async (): Promise<ApiResponse> => {
  return apiRequest('/api/health');
};

// Key API functions
export const keyApi = {
  /**
   * Redeem a license key
   */
  redeem: async (key: string): Promise<ApiResponse<{ plan: string; expiration_date: string }>> => {
    return apiRequest<{ plan: string; expiration_date: string }>('/api/keys/redeem', {
      method: 'POST',
      body: JSON.stringify({ key }),
    });
  },
};

// Release API types
export interface Release {
  version: string;
  file_name?: string;
  file_size?: number;
  added: string[];
  changed: string[];
  fixed: string[];
  date: string;
  download_url?: string;
  file_url?: string;
}

// Release API functions
export const releaseApi = {
  /**
   * Get all releases (public, no auth required)
   */
  getAll: async (): Promise<ApiResponse<{ releases: Release[] }>> => {
    return apiRequest<{ releases: Release[] }>('/api/releases/public');
  },

  /**
   * Get latest release (public, no auth required)
   */
  getLatest: async (): Promise<ApiResponse<{ release: Release | null }>> => {
    return apiRequest<{ release: Release | null }>('/api/releases/public/latest');
  },

  /**
   * Get releases for dashboard (requires auth)
   */
  getReleases: async (): Promise<ApiResponse<{ releases: Release[] }>> => {
    return apiRequest<{ releases: Release[] }>('/api/releases');
  },

  /**
   * Download a release file (requires auth and plan)
   */
  download: async (fileName: string): Promise<Blob> => {
    const token = getToken();
    const url = `${API_BASE_URL}/api/releases/download/${fileName}`;

    const response = await fetch(url, {
      headers: {
        'Authorization': `Bearer ${token}`,
      },
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.error || 'Failed to download release');
    }

    return await response.blob();
  },
};

// Payment API functions
export const paymentApi = {
  /**
   * Create lifetime checkout session
   */
  createLifetimeCheckout: async (): Promise<ApiResponse<{ sessionId: string; url: string }>> => {
    return apiRequest<{ sessionId: string; url: string }>('/api/payments/lifetime', {
      method: 'POST',
    });
  },

  /**
   * Create monthly subscription checkout
   */
  createMonthlySubscription: async (): Promise<ApiResponse<{ sessionId: string; url: string }>> => {
    return apiRequest<{ sessionId: string; url: string }>('/api/payments/monthly', {
      method: 'POST',
    });
  },

  /**
   * Get customer portal URL for managing subscription
   */
  getCustomerPortal: async (): Promise<ApiResponse<{ url: string }>> => {
    return apiRequest<{ url: string }>('/api/payments/portal', {
      method: 'POST',
    });
  },

  /**
   * Check payment status
   */
  checkPaymentStatus: async (sessionId: string): Promise<ApiResponse<{ paymentStatus: string; status: string }>> => {
    return apiRequest<{ paymentStatus: string; status: string }>(`/api/payments/status?sessionId=${encodeURIComponent(sessionId)}`);
  },

  /**
   * Get pricing information
   */
  getPrices: async (): Promise<ApiResponse<{
    prices: {
      stripe: {
        lifetime: { amount: number; currency: string; formatted: string };
        monthly: { amount: number; currency: string; formatted: string };
      };
      oxapay: {
        lifetime: { amount: number; currency: string; formatted: string };
        monthly: { amount: number; currency: string; formatted: string };
      };
    };
  }>> => {
    return apiRequest<{
      prices: {
        stripe: {
          lifetime: { amount: number; currency: string; formatted: string };
          monthly: { amount: number; currency: string; formatted: string };
        };
        oxapay: {
          lifetime: { amount: number; currency: string; formatted: string };
          monthly: { amount: number; currency: string; formatted: string };
        };
      };
    }>('/api/payments/prices');
  },
};

// OxaPay API functions
export const oxapayApi = {
  /**
   * Create lifetime invoice
   */
  createLifetimeInvoice: async (): Promise<ApiResponse<{ trackId: string; paymentUrl: string; orderId: string }>> => {
    return apiRequest<{ trackId: string; paymentUrl: string; orderId: string }>('/api/payments/oxapay/lifetime', {
      method: 'POST',
    });
  },

  /**
   * Create monthly invoice
   */
  createMonthlyInvoice: async (): Promise<ApiResponse<{ trackId: string; paymentUrl: string; orderId: string }>> => {
    return apiRequest<{ trackId: string; paymentUrl: string; orderId: string }>('/api/payments/oxapay/monthly', {
      method: 'POST',
    });
  },

  /**
   * Check payment status
   */
  checkPaymentStatus: async (orderId: string): Promise<ApiResponse<{ status: string; trackId: string; paymentUrl: string }>> => {
    return apiRequest<{ status: string; trackId: string; paymentUrl: string }>(`/api/payments/oxapay/status?orderId=${encodeURIComponent(orderId)}`);
  },
};

// Export default API object
export default {
  auth: authApi,
  key: keyApi,
  discord: discordApi,
  release: releaseApi,
  payment: paymentApi,
  oxapay: oxapayApi,
  healthCheck,
  getToken,
  setToken,
  removeToken,
};

