import React, { useState, useEffect, useRef } from 'react';
import { ViewState } from '../types';
import { FAQ_DATA } from '../constants';
import { Button, AccordionItem } from '../components/UI';
import { releaseApi, paymentApi, type Release } from '../api';
import { ChangelogModal } from '../components/ChangelogModal';
import {
  Swords,
  Zap,
  Move,
  Eye,
  ArrowRight,
  Terminal,
  Monitor,
  Youtube,
  ScrollText,
  PlayCircle,
  Wrench,
  Calendar,
  Check,
  Cpu,
  Sparkles,
  Server
} from 'lucide-react';

interface LandingProps {
  onNavigate: (view: ViewState) => void;
  isLoggedIn?: boolean;
}

const FEATURE_GROUPS = [
  {
    name: 'Combat',
    icon: Swords,
    features: [
      { name: 'Aim Assist', desc: "Aims for u" },
      { name: 'Auto Anchor', desc: "Automatically places anchors and charges them" },
      { name: 'Auto Crystal', desc: "Places and breaks end crystals automatically" },
      { name: 'Criticals', desc: "Increases chances of critical hits" },
      { name: 'Double Anchor', desc: "AKA air anchor" },
      { name: 'Mace Swap', desc: "Swaps to a mace on attack" },
      { name: 'Piercing', desc: "There is no wall but the wall." },
      { name: 'Shield Breaker', desc: "Automatically breaks shields" },
      { name: 'Spear Swap', desc: "Automatically swaps to a spear when attacking." },
      { name: 'Trigger Bot', desc: "Auto hits people." },
      { name: 'Velocity', desc: "Reduces or eliminates knockback" }
    ]
  },
  {
    name: 'Exploit',
    icon: Zap,
    features: [
      { name: 'Crosshair Optimizer', desc: "Updates Crosshair faster." },
      { name: 'Hitbox', desc: "Modify entity hitboxes" },
      { name: 'Trident Boost', desc: "Control your trident movement" }
    ]
  },
  {
    name: 'Movement',
    icon: Move,
    features: [
      { name: 'Combo Helper', desc: "Movement module that helps with comboing." },
      { name: 'Crit Assist', desc: "unsprint for u." },
      { name: 'Jump Reset', desc: "Jumps on damage" },
      { name: 'Move Fix', desc: "Fixes movement issues with silent aim" },
      { name: 'No Jump Delay', desc: "Adjust jump delay" },
      { name: 'Sprint', desc: "Sprints for you" }
    ]
  },
  {
    name: 'Render',
    icon: Eye,
    features: [
      { name: '3D ESP', desc: "3D ESP" },
      { name: 'Ambience', desc: "Make the world pretty" },
      { name: 'Glow ESP', desc: "Highlights entities with a glow effect" },
      { name: 'Hitbox Render', desc: "Renders entity hitboxes" },
      { name: 'HUD', desc: "Stuff on your screen" },
      { name: 'Name Tags', desc: "Shows nametags" },
      { name: 'No Render', desc: "Stops rendering certain elements" },
      { name: 'Target ESP', desc: "Renders stuff around your target." },
      { name: 'View Model', desc: "Customizes the held item view" },
      { name: 'Walksy Pop Chams', desc: "Renders chams on totem pops" }
    ]
  },
  {
    name: 'Utility',
    icon: Wrench,
    features: [
      { name: 'Auto Chestplate', desc: "Automatically equips a chestplate" },
      { name: 'Auto Tool', desc: "Automatically selects the best tool" },
      { name: 'Auto Totem', desc: "Automatically equips a totem of undying when needed." },
      { name: 'Chest Stealer', desc: "Steals items from chests" },
      { name: 'Easy Elytra', desc: "Makes elytra fly easier" },
      { name: 'Elytra Swap', desc: "Easily swap equipment" },
      { name: 'Fast EXP', desc: "I NEED EXP" },
      { name: 'Fast Place', desc: "Fast place blocks" },
      { name: 'Key Charge', desc: "Charges items with key" },
      { name: 'Key Pearl Macro', desc: "Throws pearls for you" },
      { name: 'No Crystal Bob', desc: "Removes crystal bobbing" },
      { name: 'Pearl Catch', desc: "Perfectly throws wind charges to hit pearls" },
      { name: 'Pot Refill', desc: "Easily refill pots" },
      { name: 'Shield Helper', desc: "Shield on key" },
      { name: 'Smart Pot', desc: "Automatically gets pots for u" },
      { name: 'Stun Web', desc: "Spidey" }
    ]
  },
  {
    name: 'Client',
    icon: Monitor,
    features: [
      { name: 'Capes', desc: "Custom capes for you" },
      { name: 'Click GUI', desc: "Crazy gui", active: true },
      { name: 'Command Logger', desc: "Logs executed commands with timestamps" },
      { name: 'Export', desc: "Exports feature data to JSON" },
      { name: 'Friends', desc: "Manage your friends list" },
      { name: 'Prevent', desc: "Prevents various things" },
      { name: 'Teams', desc: "Adds team checks for other features." },
      { name: 'Transactions', desc: "Shows transaction packets" }
    ]
  }
];

const ScrollReveal: React.FC<{
  children: React.ReactNode;
  className?: string;
  delay?: number;
  direction?: 'up' | 'down' | 'left' | 'right' | 'none';
  fullWidth?: boolean;
}> = ({ children, className = '', delay = 0, direction = 'up', fullWidth = false }) => {
  const [isVisible, setIsVisible] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsVisible(true);
          observer.unobserve(entry.target);
        }
      },
      {
        threshold: 0.1,
        rootMargin: '0px 0px -50px 0px',
      }
    );

    if (ref.current) {
      observer.observe(ref.current);
    }

    return () => {
      if (ref.current) {
        observer.unobserve(ref.current);
      }
    };
  }, []);

  const getTransform = () => {
    if (direction === 'up') return 'translate-y-12';
    if (direction === 'down') return '-translate-y-12';
    if (direction === 'left') return '-translate-x-12';
    if (direction === 'right') return 'translate-x-12';
    return '';
  };

  return (
    <div
      ref={ref}
      className={`${fullWidth ? 'w-full' : ''} transition-all duration-1000 ease-out ${className} ${
        isVisible ? 'opacity-100 transform-none' : `opacity-0 ${getTransform()}`
      }`}
      style={{ transitionDelay: `${delay}ms` }}
    >
      {children}
    </div>
  );
};

export const Landing: React.FC<LandingProps> = ({ onNavigate, isLoggedIn }) => {
  const [openFAQ, setOpenFAQ] = useState<string | null>('1');
  const [releases, setReleases] = useState<Release[]>([]);
  const [releasesLoading, setReleasesLoading] = useState(false);
  const [selectedRelease, setSelectedRelease] = useState<Release | null>(null);
  const [isChangelogModalOpen, setIsChangelogModalOpen] = useState(false);
  const [prices, setPrices] = useState<{
    stripe: {
      lifetime: { formatted: string; currency: string };
      monthly: { formatted: string; currency: string };
    };
    oxapay: {
      lifetime: { formatted: string; currency: string };
      monthly: { formatted: string; currency: string };
    };
  } | null>(null);
  const [pricesLoading, setPricesLoading] = useState(true);

  useEffect(() => {
    fetchReleases();
    fetchPrices();
  }, []);

  const fetchReleases = async () => {
    setReleasesLoading(true);
    try {
      const response = await releaseApi.getAll();
      if (response.success && response.releases) {
        setReleases(response.releases.slice(0, 10)); // Show latest 10
      }
    } catch (error) {
      console.error('Error fetching releases:', error);
    } finally {
      setReleasesLoading(false);
    }
  };

  const fetchPrices = async () => {
    setPricesLoading(true);
    try {
      const response = await paymentApi.getPrices();
      if (response.success && response.prices) {
        setPrices(response.prices);
      }
    } catch (error) {
      console.error('Error fetching prices:', error);
    } finally {
      setPricesLoading(false);
    }
  };

  const handleViewChangelog = (release: Release) => {
    setSelectedRelease(release);
    setIsChangelogModalOpen(true);
  };



  return (
    <div className="min-h-screen bg-echo-bg overflow-x-hidden selection:bg-echo-primary selection:text-black">
      <ChangelogModal
        isOpen={isChangelogModalOpen}
        onClose={() => {
          setIsChangelogModalOpen(false);
          setSelectedRelease(null);
        }}
        release={selectedRelease}
      />
      
      <section id="overview" className="relative min-h-screen pt-32 pb-16 lg:pt-48 lg:pb-32 px-4 sm:px-6 lg:px-8 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-blue-900/10 via-echo-bg to-echo-bg opacity-50 pointer-events-none"></div>
        <div className="max-w-5xl mx-auto relative z-10">
          <div className="text-center flex flex-col items-center">

             <div className="mb-8 px-4 py-2.5 rounded-full border border-echo-primary/20 bg-echo-primary/5 backdrop-blur-sm inline-flex items-center gap-2.5">
               <span className="relative flex h-2 w-2">
                 <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-echo-primary opacity-75"></span>
                 <span className="relative inline-flex rounded-full h-2 w-2 bg-echo-primary"></span>
               </span>
               <span className="text-sm text-echo-secondary font-medium tracking-wide">In Development — Releasing Soon</span>
             </div>

             <ScrollReveal>

                
                <h1 className="text-5xl md:text-7xl lg:text-8xl font-black text-transparent bg-clip-text bg-gradient-to-b from-white via-white to-white/50 tracking-tighter mb-6 pb-2">
                  The unseen advantage.
                </h1>
                
                <p className="text-lg md:text-xl text-echo-muted font-sans max-w-2xl mx-auto leading-relaxed mb-10">
                  Professional grade assistance for Minecraft. Undetectable by design.
                  <br className="hidden md:block" />
                  Engineered for sophisticated evasion and fluid movement.
                </p>

                <div className="flex flex-col sm:flex-row gap-4 w-full sm:w-auto justify-center">
                  <Button size="lg" className="rounded-full px-8 h-12 text-base shadow-[0_0_20px_rgba(255,255,255,0.1)] hover:shadow-[0_0_30px_rgba(255,255,255,0.2)] transition-shadow" onClick={() => onNavigate(isLoggedIn ? 'DASHBOARD' : 'REGISTER')}>
                    {isLoggedIn ? 'Dashboard' : 'Get Started'} <ArrowRight className="w-4 h-4 ml-2" />
                  </Button>
                  <Button size="lg" variant="outline" className="rounded-full px-8 h-12 text-base backdrop-blur-sm bg-white/[0.01]" onClick={() => {
                      document.getElementById('features')?.scrollIntoView({ behavior: 'smooth' });
                  }}>
                    Explore Features
                  </Button>
                </div>

                {/* Pricing Display */}
                {!pricesLoading && prices && (
                  <ScrollReveal delay={600} className="mt-12">
                    <div className="flex items-center justify-center gap-6 text-sm text-echo-muted">
                      <div className="flex items-center gap-2">
                        <span className="text-white font-semibold">Monthly:</span>
                        <span className="text-echo-primary font-bold">€{prices.stripe.monthly.formatted}/mo</span>
                      </div>
                      <div className="w-px h-4 bg-white/10"></div>
                      <div className="flex items-center gap-2">
                        <span className="text-white font-semibold">Lifetime:</span>
                        <span className="text-echo-primary font-bold">€{prices.stripe.lifetime.formatted}</span>
                      </div>
                    </div>
                  </ScrollReveal>
                )}
             </ScrollReveal>

             <ScrollReveal delay={300} className="mt-24 w-full max-w-3xl">
                <div className="bg-white/[0.02] backdrop-blur-md border border-white/5 rounded-3xl p-6 md:p-8 text-left relative overflow-hidden">
                   <div className="absolute top-0 w-full h-px bg-gradient-to-r from-transparent via-white/10 to-transparent left-0"></div>
                   
                   <div className="flex items-center justify-between mb-8">
                      <h3 className="text-xl font-bold text-white">What's New</h3>
                   </div>

                   <div className="relative border-l border-white/10 ml-3 space-y-8 pb-2">
                       {releasesLoading ? (
                         <div className="pl-8 text-sm text-echo-muted">Loading updates...</div>
                       ) : releases.map((release, idx) => (
                         <div key={idx} className="relative pl-8 group cursor-pointer" onClick={() => handleViewChangelog(release)}>
                            {/* Dot */}
                            <div className={`absolute -left-[5px] top-1.5 w-2.5 h-2.5 rounded-full border-2 border-echo-bg transition-colors ${idx === 0 ? 'bg-echo-primary shadow-[0_0_10px_rgba(59,130,246,0.5)]' : 'bg-white/20 group-hover:bg-white'}`}></div>
                            
                            <div className="flex items-baseline gap-3 mb-1">
                               <span className="text-sm font-bold text-white group-hover:text-echo-primary transition-colors">{release.version}</span>
                               <span className="text-xs text-echo-muted font-sans">{new Date(release.date).toLocaleDateString()}</span>
                            </div>
                            
                            <div className="text-sm text-echo-muted group-hover:text-echo-text transition-colors line-clamp-2">
                               {release.added && release.added.length > 0 ? (
                                 <span>Added {release.added.join(', ')}. </span>
                               ) : release.fixed && release.fixed.length > 0 ? (
                                 <span>Fixed {release.fixed.join(', ')}. </span>
                               ) : <span>Performance improvements and bug fixes.</span>}
                            </div>
                         </div>
                       ))}
                   </div>
                </div>
             </ScrollReveal>
          </div>
        </div>
      </section>

      {/* Features + Review */}
      <section id="features" className="min-h-screen bg-echo-bg relative overflow-hidden flex flex-col justify-center py-16">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 w-full">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-16">
            <ScrollReveal delay={0}>
              <div className="group relative bg-white/[0.02] backdrop-blur-md border border-white/5 rounded-2xl p-8 hover:border-echo-primary/30 transition-all duration-500 h-full">
                <div className="absolute inset-0 bg-gradient-to-br from-echo-primary/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500 rounded-2xl pointer-events-none"></div>
                <div className="relative z-10">
                  <div className="w-10 h-10 rounded-xl bg-echo-primary/10 border border-echo-primary/20 flex items-center justify-center mb-5">
                    <Cpu className="w-5 h-5 text-echo-primary" />
                  </div>
                  <h3 className="text-lg font-bold text-white mb-2">Optimized Rendering</h3>
                  <p className="text-sm text-echo-muted leading-relaxed">
                    Heavily optimized rendering pipeline. We take performance seriously and ensure no compromise with our rendering.
                  </p>
                </div>
              </div>
            </ScrollReveal>

            <ScrollReveal delay={150}>
              <div className="group relative bg-white/[0.02] backdrop-blur-md border border-white/5 rounded-2xl p-8 hover:border-echo-primary/30 transition-all duration-500 h-full">
                <div className="absolute inset-0 bg-gradient-to-br from-echo-primary/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500 rounded-2xl pointer-events-none"></div>
                <div className="relative z-10">
                  <div className="w-10 h-10 rounded-xl bg-echo-primary/10 border border-echo-primary/20 flex items-center justify-center mb-5">
                    <Sparkles className="w-5 h-5 text-echo-primary" />
                  </div>
                  <h3 className="text-lg font-bold text-white mb-2">Premium Modules</h3>
                  <p className="text-sm text-echo-muted leading-relaxed">
                    Carefully crafted features that are easy to use. Every module is tested and refined for a premium experience.
                  </p>
                </div>
              </div>
            </ScrollReveal>

            <ScrollReveal delay={300}>
              <div className="group relative bg-white/[0.02] backdrop-blur-md border border-white/5 rounded-2xl p-8 hover:border-echo-primary/30 transition-all duration-500 h-full">
                <div className="absolute inset-0 bg-gradient-to-br from-echo-primary/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500 rounded-2xl pointer-events-none"></div>
                <div className="relative z-10">
                  <div className="w-10 h-10 rounded-xl bg-echo-primary/10 border border-echo-primary/20 flex items-center justify-center mb-5">
                    <Server className="w-5 h-5 text-echo-primary" />
                  </div>
                  <h3 className="text-lg font-bold text-white mb-2">Donut SMP Ready</h3>
                  <p className="text-sm text-echo-muted leading-relaxed">
                    Server-specific features and macros built for Donut SMP. Automated mechanics to stay on top.
                  </p>
                </div>
              </div>
            </ScrollReveal>
          </div>

          <div className="max-w-7xl mx-auto relative z-10">
          <ScrollReveal>
            <div className="text-center mb-16">
              <h2 className="text-4xl md:text-6xl font-black text-white uppercase tracking-tighter mb-4">
                Made By The <span className="text-echo-primary">Biggest Reviewer</span>
              </h2>
              <div className="w-24 h-1 bg-echo-primary mx-auto mb-6"></div>
              <p className="text-echo-muted text-sm uppercase tracking-widest">
                TRUSTED BY THE COMMUNITY 
              </p>
            </div>
          </ScrollReveal>

          <ScrollReveal>
            <div 
              className="block group relative bg-white/[0.02] backdrop-blur-md border border-white/5 rounded-3xl p-8 md:p-12 overflow-hidden transition-all hover:border-white/10"
            >
              <div className="absolute inset-0 bg-echo-primary/5 opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none"></div>
              
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 items-center relative z-10">
                
                <div className="space-y-8">
                  <a 
                    href="https://www.youtube.com/@cydeYT" 
                    target="_blank" 
                    rel="noopener noreferrer" 
                    className="flex items-center space-x-5 w-fit group/yt p-4 rounded-2xl hover:bg-white/5 transition-colors border border-transparent hover:border-white/5"
                  >
                     <div className="relative">
                          <Youtube className="w-12 h-12 text-red-500" strokeWidth={1} />
                     </div>
                     <div>
                       <h3 className="text-xl font-bold text-white tracking-tight flex items-center gap-2">
                         Cyde <span className="text-xs font-normal text-echo-muted/70 px-2 py-0.5 rounded-full bg-white/5 border border-white/5">Owner</span>
                       </h3>
                       <p className="text-sm text-echo-muted font-sans group-hover/yt:text-red-400 transition-colors">@cydeYT</p>
                     </div>
                  </a>

                  <p className="text-echo-text font-sans text-lg leading-relaxed max-w-md">
                     Echo is engineered based on deep analysis of detection systems and mechanics, backed by over 
                     <span className="text-white font-bold mx-1">150+ reviews</span> 
                     of the best (and worst) clients in the scene.
                  </p>

                  <div className="flex gap-8 border-t border-white/5 pt-6 mt-2">
                     <div className="flex flex-col">
                        <span className="text-2xl font-bold text-white">75K+</span>
                        <span className="text-xs text-echo-muted font-sans">Subscribers</span>
                     </div>
                     <div className="flex flex-col">
                        <span className="text-2xl font-bold text-white">5.3M+</span>
                        <span className="text-xs text-echo-muted font-sans">Total Views</span>
                     </div>
                     <div className="flex flex-col">
                        <span className="text-2xl font-bold text-white">152+</span>
                        <span className="text-xs text-echo-muted font-sans">Videos</span>
                     </div>
                  </div>
                </div>

                <div className="relative w-full rounded-2xl overflow-hidden border border-white/5 shadow-2xl group-hover:border-white/10 transition-colors aspect-video">
                     <iframe 
                       className="w-full h-full" 
                       src="https://www.youtube.com/embed/W3HXvVELXB8" 
                       title="Meteor Client Review" 
                       allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                       allowFullScreen
                     ></iframe>
                </div>

              </div>

              <a 
                href="https://www.youtube.com/@cydeYT" 
                target="_blank" 
                rel="noopener noreferrer" 
                className="absolute bottom-6 right-8 flex items-center space-x-2 text-echo-primary text-sm opacity-0 group-hover:opacity-100 transition-all duration-500 translate-x-4 group-hover:translate-x-0 z-30"
              >
                 <span>VISIT CHANNEL</span>
                 <ArrowRight className="w-4 h-4" />
              </a>
            </div>
          </ScrollReveal>
          </div>
        </div>
      </section>

      {/* Pricing Section */}
      <section id="pricing" className="py-24 bg-echo-bg border-t border-echo-border relative overflow-hidden">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8">
          <ScrollReveal>
            <div className="text-center mb-16">
              <h2 className="text-4xl md:text-6xl font-black text-white uppercase tracking-tighter mb-4">
                Simple <span className="text-echo-primary">Pricing</span>
              </h2>
              <div className="w-24 h-1 bg-echo-primary mx-auto mb-6"></div>
              <p className="text-echo-muted text-sm uppercase tracking-widest">
                Choose Your Plan
              </p>
            </div>
          </ScrollReveal>

          <div className="grid md:grid-cols-2 gap-6 max-w-4xl mx-auto">
            {/* Monthly Plan */}
            <ScrollReveal delay={200}>
              <div className="bg-white/[0.02] backdrop-blur-md border-2 border-echo-primary/50 rounded-2xl p-8 hover:border-echo-primary transition-all relative">
                <div className="absolute top-4 right-4 px-3 py-1 bg-echo-primary/20 text-echo-primary text-xs font-bold rounded-full">
                  BEST VALUE
                </div>
                <div className="mb-6">
                  <h3 className="text-2xl font-bold text-white mb-4">Monthly</h3>
                  {pricesLoading ? (
                    <div className="h-12 w-32 bg-white/5 rounded animate-pulse"></div>
                  ) : prices ? (
                    <div className="flex items-baseline gap-2">
                      <span className="text-4xl font-bold text-white">€{prices.stripe.monthly.formatted}</span>
                      <span className="text-echo-muted">/month</span>
                    </div>
                  ) : (
                    <div className="flex items-baseline gap-2">
                      <span className="text-4xl font-bold text-white">€6.70</span>
                      <span className="text-echo-muted">/month</span>
                    </div>
                  )}
                </div>
                <ul className="space-y-3 mb-8">
                  <li className="flex items-center gap-2 text-sm text-echo-muted">
                    <Check className="w-4 h-4 text-echo-primary flex-shrink-0" />
                    <span>Premium features</span>
                  </li>
                  <li className="flex items-center gap-2 text-sm text-echo-muted">
                    <Check className="w-4 h-4 text-echo-primary flex-shrink-0" />
                    <span>Cancel anytime</span>
                  </li>
                  <li className="flex items-center gap-2 text-sm text-echo-muted">
                    <Check className="w-4 h-4 text-echo-primary flex-shrink-0" />
                    <span>Auto-renewal</span>
                  </li>
                </ul>
                <Button
                  fullWidth
                  variant="primary"
                  onClick={() => onNavigate(isLoggedIn ? 'DASHBOARD' : 'REGISTER')}
                  className="rounded-xl"
                >
                  {isLoggedIn ? 'Dashboard' : 'Get Started'}
                </Button>
              </div>
            </ScrollReveal>

            {/* Lifetime Plan */}
            <ScrollReveal delay={400}>
              <div className="bg-white/[0.02] backdrop-blur-md border border-white/5 rounded-2xl p-8 hover:border-white/10 transition-all">
                <div className="mb-6">
                  <h3 className="text-2xl font-bold text-white mb-4">Lifetime</h3>
                  {pricesLoading ? (
                    <div className="h-12 w-32 bg-white/5 rounded animate-pulse"></div>
                  ) : prices ? (
                    <div className="flex items-baseline gap-2">
                      <span className="text-4xl font-bold text-white">€{prices.stripe.lifetime.formatted}</span>
                      <span className="text-echo-muted">one-time</span>
                    </div>
                  ) : (
                    <div className="flex items-baseline gap-2">
                      <span className="text-4xl font-bold text-white">€35.00</span>
                      <span className="text-echo-muted">one-time</span>
                    </div>
                  )}
                </div>
                <ul className="space-y-3 mb-8">
                  <li className="flex items-center gap-2 text-sm text-echo-muted">
                    <Check className="w-4 h-4 text-echo-primary flex-shrink-0" />
                    <span>Premium features forever</span>
                  </li>
                  <li className="flex items-center gap-2 text-sm text-echo-muted">
                    <Check className="w-4 h-4 text-echo-primary flex-shrink-0" />
                    <span>No recurring payments</span>
                  </li>
                  <li className="flex items-center gap-2 text-sm text-echo-muted">
                    <Check className="w-4 h-4 text-echo-primary flex-shrink-0" />
                    <span>All future updates</span>
                  </li>
                </ul>
                <Button
                  fullWidth
                  variant="outline"
                  onClick={() => onNavigate(isLoggedIn ? 'DASHBOARD' : 'REGISTER')}
                  className="rounded-xl"
                >
                  {isLoggedIn ? 'Dashboard' : 'Get Started'}
                </Button>
              </div>
            </ScrollReveal>
          </div>
        </div>
      </section>

      <section id="faq" className="py-24 bg-echo-bg border-t border-echo-border">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
          <ScrollReveal>
            <div className="flex items-center space-x-4 mb-12">
               <Terminal className="w-6 h-6 text-echo-primary" />
               <h2 className="text-2xl font-bold text-white uppercase tracking-wider">FAQ</h2>
            </div>
          </ScrollReveal>
          
          <ScrollReveal delay={200}>
            <div className="border-t border-echo-border">
              {FAQ_DATA.map((item, idx) => (
                <AccordionItem
                  key={item.id}
                  index={`0${idx + 1}`}
                  question={item.question}
                  answer={item.answer}
                  isOpen={openFAQ === item.id}
                  onClick={() => setOpenFAQ(openFAQ === item.id ? null : item.id)}
                />
              ))}
            </div>
          </ScrollReveal>
        </div>
      </section>

      <footer className="bg-echo-surface py-16 border-t border-echo-border">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
           <div className="grid grid-cols-1 md:grid-cols-4 gap-12 mb-12">
              <div className="col-span-1 md:col-span-2">
                 <div className="font-sans font-bold text-3xl text-white tracking-tighter mb-6">
                    [ ECHO ]
                 </div>
                 <p className="text-echo-muted text-sm max-w-xs">
                    Premium evasion software for Minecraft Java Edition. Engineered for competitive play.
                 </p>
              </div>
              
              <div>
                 <h4 className="font-sans text-xs text-echo-white uppercase mb-6">Resources</h4>
                 <ul className="space-y-4 text-sm text-echo-muted">
                    <li className="hover:text-echo-primary cursor-pointer transition-colors">Documentation</li>
                    <li className="hover:text-echo-primary cursor-pointer transition-colors">API Reference</li>
                    <li className="hover:text-echo-primary cursor-pointer transition-colors">System Status</li>
                 </ul>
              </div>

              <div>
                 <h4 className="font-sans text-xs text-echo-white uppercase mb-6">Legal</h4>
                 <ul className="space-y-4 text-sm text-echo-muted">
                    <li className="hover:text-echo-primary cursor-pointer transition-colors">Terms of Service</li>
                    <li className="hover:text-echo-primary cursor-pointer transition-colors">Privacy Policy</li>
                 </ul>
              </div>
           </div>
           
           <div className="border-t border-echo-border pt-8 flex justify-center items-center text-xs text-echo-muted">
              <p>© 2025 Cyde. All rights reserved.</p>
           </div>
        </div>
      </footer>
    </div>
  );
};
