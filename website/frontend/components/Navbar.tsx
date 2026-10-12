import React, { useState, useEffect } from 'react';
import { ViewState } from '../types';
import { Button } from './UI';
import { Menu, X } from 'lucide-react';

interface NavbarProps {
  currentView: ViewState;
  onNavigate: (view: ViewState) => void;
  isLoggedIn: boolean;
}

export const Navbar: React.FC<NavbarProps> = ({ currentView, onNavigate, isLoggedIn }) => {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 20);
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const navLinks = [
    { label: 'Overview', view: 'LANDING' as ViewState, sectionId: 'overview' },
    { label: 'Features', view: 'LANDING' as ViewState, sectionId: 'features' }, 
    { label: 'FAQ', view: 'LANDING' as ViewState, sectionId: 'faq' },
  ];

  const handleNav = (view: ViewState, sectionId?: string) => {
    if (currentView === view) {
      if (sectionId) {
        document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth' });
        try {
          window.history.replaceState({}, '', '/#' + sectionId);
        } catch (e) {
          console.error('Navigation error:', e);
        }
      } else {
        window.scrollTo({ top: 0, behavior: 'smooth' });
        try {
          window.history.replaceState({}, '', '/');
        } catch (e) {
          console.error('Navigation error:', e);
        }
      }
    } else {
      onNavigate(view);
      if (sectionId) {
        setTimeout(() => {
          document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth' });
        }, 100);
      }
    }
    setMobileMenuOpen(false);
  };

  return (
    <>
      <nav 
        className={`fixed top-6 left-1/2 -translate-x-1/2 w-[90%] max-w-4xl z-50 transition-all duration-300 ease-in-out
          ${scrolled ? 'bg-black/60 backdrop-blur-xl border border-white/10 shadow-[0_20px_40px_-10px_rgba(0,0,0,0.5)]' : 'bg-transparent border border-transparent'}
          rounded-full px-6 h-14 flex items-center justify-between
        `}
      >
        <div 
          className="flex items-center cursor-pointer group" 
          onClick={() => handleNav('LANDING', 'overview')}
        >
          <img src="/logo.png" alt="Echo" className="w-8 h-8 mr-2 object-contain" />
          <span className="font-sans font-bold text-lg text-white tracking-tight">Echo</span>
        </div>

        <div className="hidden md:flex items-center space-x-1 absolute left-1/2 -translate-x-1/2">
          {navLinks.map((link) => (
            <button
              key={link.label}
              onClick={() => handleNav(link.view, link.sectionId)}
              className="px-4 py-1.5 text-sm font-sans font-medium text-echo-text hover:text-white transition-colors rounded-full hover:bg-white/5"
            >
              {link.label}
            </button>
          ))}
        </div>

        <div className="hidden md:flex items-center gap-3">
          {!isLoggedIn && currentView !== 'DASHBOARD' && (
            <>
              <button 
                onClick={() => handleNav('LOGIN')} 
                className="text-sm font-sans font-medium text-echo-text hover:text-white transition-colors px-3"
              >
                Log In
              </button>
              <Button size="sm" onClick={() => handleNav('REGISTER')} className="rounded-full h-8 text-xs bg-white text-black hover:bg-gray-200">
                Get Access
              </Button>
            </>
          )}
          {isLoggedIn && currentView !== 'DASHBOARD' && (
            <Button size="sm" onClick={() => handleNav('DASHBOARD')} className="rounded-full h-8 text-xs bg-white text-black hover:bg-gray-200">
              Dashboard
            </Button>
          )}
           {currentView === 'DASHBOARD' && (
             <div className="flex items-center">
                <div className="w-2 h-2 bg-green-500 rounded-full mr-2 shadow-[0_0_10px_rgba(34,197,94,0.5)]"></div>
             </div>
           )}
        </div>

        <div className="md:hidden flex items-center">
          <button
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            className="text-white focus:outline-none"
          >
            {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
        </div>
      </nav>

      {mobileMenuOpen && (
        <div className="fixed inset-0 z-40 bg-black/90 backdrop-blur-xl flex flex-col items-center justify-center space-y-8 animate-in fade-in duration-200">
          {navLinks.map((link) => (
            <button
              key={link.label}
              onClick={() => handleNav(link.view, link.sectionId)}
              className="text-2xl font-sans font-medium text-white hover:text-echo-primary transition-colors"
            >
              {link.label}
            </button>
          ))}
          <div className="flex flex-col gap-4 mt-8 w-64">
            {!isLoggedIn ? (
              <>
                <Button onClick={() => handleNav('LOGIN')} variant="outline" fullWidth className="rounded-xl h-12">Log In</Button>
                <Button onClick={() => handleNav('REGISTER')} fullWidth className="rounded-xl h-12">Get Access</Button>
              </>
            ) : (
              <Button onClick={() => handleNav('DASHBOARD')} fullWidth className="rounded-xl h-12 bg-white text-black hover:bg-gray-200">Dashboard</Button>
            )}
          </div>
        </div>
      )}
    </>
  );
};