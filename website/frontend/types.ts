export type ViewState = 'LANDING' | 'LOGIN' | 'REGISTER' | 'DASHBOARD';

export type DashboardSection = 'HOME' | 'INSTALL' | 'DOCS' | 'CHANGELOG' | 'SETTINGS';

export interface FAQItem {
  id: string;
  question: string;
  answer: string;
}

export interface FeatureItem {
  title: string;
  description: string;
  icon: string;
  colSpan?: number; 
}