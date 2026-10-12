import React, { useState, useEffect, useCallback } from 'react';
import { Navbar } from './components/Navbar';
import { Landing } from './pages/Landing';
import { Auth } from './pages/Auth';
import { Dashboard } from './pages/Dashboard';
import { ViewState } from './types';
import { getToken, authApi, removeToken, setToken, discordApi } from './api';

function App() {
  const [currentView, setCurrentView] = useState<ViewState>('LANDING');
  const [isCheckingAuth, setIsCheckingAuth] = useState(true);
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  const viewToPath = (view: ViewState) => {
    switch (view) {
      case 'LANDING':
        return '/';
      case 'LOGIN':
        return '/auth';
      case 'REGISTER':
        return '/onboard';
      case 'DASHBOARD':
        return '/dashboard';
      default:
        return '/';
    }
  };

  const pathToView = (path: string): ViewState => {
    if (path.startsWith('/dashboard')) return 'DASHBOARD';
    if (path.startsWith('/onboard')) return 'REGISTER';
    if (path.startsWith('/auth')) return 'LOGIN';
    return 'LANDING';
  };

  const navigate = useCallback((view: ViewState, replace = false) => {
    setCurrentView(view);
    const path = viewToPath(view);
    try {
      if (replace) window.history.replaceState({}, '', path);
      else window.history.pushState({}, '', path);
    } catch (e) {
      console.error('Navigation error:', e);
    }
    // Update isLoggedIn based on token and view
    const token = getToken();
    if (view === 'DASHBOARD' && token) {
      setIsLoggedIn(true);
    } else if (view === 'LANDING' && !token) {
      setIsLoggedIn(false);
    }
  }, []);

  useEffect(() => {
    const checkAuth = async () => {
      // Check if this is a Discord callback from backend redirect
      const urlParams = new URLSearchParams(window.location.search);
      const discordLinked = urlParams.get('discord_linked');
      const discordLogin = urlParams.get('discord_login');
      const error = urlParams.get('error');
      const message = urlParams.get('message');
      const initialView = pathToView(window.location.pathname + window.location.search);
      
      // Handle Discord login callback with authorization code
      const authCode = urlParams.get('code');
      if (discordLogin === 'true' && authCode) {
        // Exchange authorization code for session token
        try {
          const exchangeResponse = await discordApi.exchangeCode(authCode);
          if (exchangeResponse.success && exchangeResponse.sessionToken) {
            // Store the token
            setToken(exchangeResponse.sessionToken);

            // Verify the token works before navigating
            const response = await authApi.getCurrentUser();
            if (response.success && response.user) {
              setIsLoggedIn(true);
              if (initialView === 'LOGIN' || initialView === 'REGISTER') {
                navigate('DASHBOARD', true);
              } else {
                setCurrentView(initialView);
                try {
                  window.history.replaceState({}, '', viewToPath(initialView));
                } catch {}
              }
            } else {
              // Token is invalid
              setIsLoggedIn(false);
              removeToken();
              navigate('LOGIN', true);
            }
          } else {
            // Code exchange failed
            setIsLoggedIn(false);
            removeToken();
            navigate('LOGIN', true);
            try {
              window.history.replaceState({}, '', '/auth?error=login_failed');
            } catch {}
          }
        } catch (error) {
          // Network error or invalid code
          setIsLoggedIn(false);
          console.error('Discord login exchange error:', error);
          removeToken();
          navigate('LOGIN', true);
          try {
            window.history.replaceState({}, '', '/auth?error=login_failed');
          } catch {}
        }
        setIsCheckingAuth(false);
        return;
      }
      
      if (discordLinked === 'true') {
        // Clean URL and go to dashboard
        // The Dashboard component will handle showing the success message
        navigate('DASHBOARD', true);
        setIsCheckingAuth(false);
        return;
      }
      
      if (error) {
        // Clean URL and show error - redirect to auth page if it's a login error
        setIsLoggedIn(false);
        if (error.includes('discord_login') || error.includes('no_code') || error.includes('no_state')) {
          navigate('LOGIN', true);
          // append error message to url
          try {
            window.history.replaceState({}, '', '/auth?error=' + encodeURIComponent(message || 'Discord login failed'));
          } catch {}
        } else {
          navigate('LANDING', true);
          console.error('Discord error:', error, message || 'Unknown error');
        }
        setIsCheckingAuth(false);
        return;
      }

      const existingToken = getToken();
      if (existingToken) {
        try {
          const response = await authApi.getCurrentUser();
          if (response.success && response.user) {
            setIsLoggedIn(true);
            // Only force a redirect to the dashboard if the user was on an auth route.
            // Otherwise preserve whatever route they refreshed.
            if (initialView === 'LOGIN' || initialView === 'REGISTER') {
              navigate('DASHBOARD', true);
            } else {
              setCurrentView(initialView);
              try {
                // Remove query params from URL if present
                window.history.replaceState({}, '', viewToPath(initialView));
              } catch {}
            }
          } else {
            // Token is invalid, clear it
            setIsLoggedIn(false);
            removeToken();
            navigate('LANDING', true);
          }
        } catch (error) {
          // Network error or invalid token, clear it
          setIsLoggedIn(false);
          removeToken();
          navigate('LANDING', true);
        }
      } else {
        setIsLoggedIn(false);
        setCurrentView(initialView);
      }
      setIsCheckingAuth(false);
    };

    checkAuth();
  }, []);

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [currentView]);

  // Handle back/forward navigation
  useEffect(() => {
    const onPop = () => {
      const view = pathToView(window.location.pathname + window.location.search);
      setCurrentView(view);
    };
    window.addEventListener('popstate', onPop);
    return () => window.removeEventListener('popstate', onPop);
  }, []);

  // Show loading state while checking authentication
  if (isCheckingAuth) {
    return (
      <div className="font-sans text-echo-text min-h-screen bg-echo-bg flex items-center justify-center">
        <div className="text-center">
          <div className="w-8 h-8 border-2 border-echo-primary border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
          <p className="font-sans text-echo-muted text-xs">Loading...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="font-sans text-echo-text min-h-screen bg-echo-bg">
      <Navbar currentView={currentView} onNavigate={(v) => navigate(v)} isLoggedIn={isLoggedIn} />
      
      {currentView === 'LANDING' && (
        <Landing onNavigate={(v) => navigate(v)} isLoggedIn={isLoggedIn} />
      )}

      {(currentView === 'LOGIN' || currentView === 'REGISTER') && (
        <Auth view={currentView} onNavigate={(v) => navigate(v)} />
      )}

      {currentView === 'DASHBOARD' && (
        <Dashboard onNavigate={(v) => navigate(v)} />
      )}
    </div>
  );
}

export default App;