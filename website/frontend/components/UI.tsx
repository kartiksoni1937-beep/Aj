import React from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost';
  size?: 'sm' | 'md' | 'lg';
  fullWidth?: boolean;
}

export const Button: React.FC<ButtonProps> = ({ 
  children, 
  variant = 'primary', 
  size = 'md', 
  fullWidth = false,
  className = '', 
  ...props 
}) => {
  const baseStyles = "inline-flex items-center justify-center font-sans font-medium transition-all focus:outline-none disabled:opacity-50 disabled:pointer-events-none rounded-2xl";
  
  const variants = {
    primary: "bg-white text-black hover:bg-gray-200 shadow-[0_0_15px_rgba(255,255,255,0.1)]",
    secondary: "bg-white/5 text-echo-white border border-white/5 hover:bg-white/10 hover:border-white/10",
    outline: "bg-transparent border border-echo-border text-echo-text hover:border-echo-white hover:text-echo-white",
    ghost: "bg-transparent border-transparent text-echo-text hover:text-echo-primary hover:bg-white/5"
  };

  const sizes = {
    sm: "h-8 px-4 text-xs",
    md: "h-10 px-5 text-sm",
    lg: "h-12 px-8 text-base"
  };

  return (
    <button 
      className={`${baseStyles} ${variants[variant]} ${sizes[size]} ${fullWidth ? 'w-full' : ''} ${className}`} 
      {...props}
    >
      {children}
    </button>
  );
};

interface CardProps {
  children: React.ReactNode;
  className?: string;
  hoverEffect?: boolean;
  noBorder?: boolean;
  onClick?: () => void;
  // Optional overlay rendered as a sibling to the content wrapper so it can sit above everything
  overlay?: React.ReactNode;
}

export const Card: React.FC<CardProps> = ({ children, className = '', hoverEffect = false, noBorder = false, onClick, overlay }) => {
  return (
    <div 
      className={`relative bg-white/[0.03] backdrop-blur-md rounded-2xl overflow-hidden ${!noBorder ? 'border border-white/5' : ''} ${hoverEffect || onClick ? 'group hover:bg-white/[0.05] transition-colors duration-300' : ''} ${className}`}
      onClick={onClick}
    >
      {/* Render overlay as a direct child of the Card root so it can overlay the content properly */}
      {overlay}
      <div className="relative z-10">
        {children}
      </div>
    </div>
  );
};

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  icon?: React.ReactNode;
}

export const Input: React.FC<InputProps> = ({ label, icon, className = '', ...props }) => {
  return (
    <div className="w-full space-y-2">
      {label && <label className="text-xs font-medium text-echo-muted uppercase tracking-wide">{label}</label>}
      <div className="relative group">
        {icon && (
          <div className="absolute left-4 top-1/2 -translate-y-1/2 text-echo-muted group-focus-within:text-echo-primary transition-colors">
            {icon}
          </div>
        )}
        <input
          className={`w-full bg-white/[0.03] border border-white/5 text-echo-white font-sans text-sm py-3 ${icon ? 'pl-11' : 'px-4'} pr-4 placeholder-echo-muted/50 focus:outline-none focus:border-echo-primary/50 focus:ring-1 focus:ring-echo-primary/50 transition-all rounded-xl ${className}`}
          {...props}
        />
      </div>
    </div>
  );
};

interface AccordionItemProps {
  question: string;
  answer: string;
  isOpen: boolean;
  onClick: () => void;
  index?: string;
}

export const AccordionItem: React.FC<AccordionItemProps> = ({ question, answer, isOpen, onClick, index }) => {
  return (
    <div className="border-b border-white/5 last:border-0 bg-transparent">
      <button
        className="w-full py-4 flex items-center justify-between text-left focus:outline-none group"
        onClick={onClick}
      >
        <div className="flex items-center gap-4">
          <span className={`font-medium font-sans text-sm transition-colors ${isOpen ? 'text-white' : 'text-echo-text group-hover:text-white'}`}>
            {question}
          </span>
        </div>
        {isOpen ? (
          <ChevronUp className="w-4 h-4 text-echo-primary" />
        ) : (
          <ChevronDown className="w-4 h-4 text-echo-muted group-hover:text-white" />
        )}
      </button>
      <div
        className={`overflow-hidden transition-all duration-300 ease-in-out ${isOpen ? 'max-h-96 opacity-100 mb-4' : 'max-h-0 opacity-0'}`}
      >
        <div className="text-echo-text font-sans text-sm leading-relaxed pl-0">
          {answer}
        </div>
      </div>
    </div>
  );
};

export const EchoLogo: React.FC<{ className?: string }> = ({ className = "w-8 h-8" }) => (
  <svg 
    viewBox="0 0 24 24" 
    fill="none" 
    stroke="currentColor" 
    strokeWidth="2" 
    strokeLinecap="round" 
    strokeLinejoin="round" 
    className={className}
    xmlns="http://www.w3.org/2000/svg"
  >
    <circle cx="12" cy="12" r="10" strokeOpacity="0.2"/>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2v4" />
    <path d="M12 18v4" />
    <path d="M2 12h4" />
    <path d="M18 12h4" />
  </svg>
);