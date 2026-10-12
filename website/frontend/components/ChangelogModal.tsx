import React from 'react';
import { X, Plus, Minus, Wrench } from 'lucide-react';
import { Release } from '../api';

interface ChangelogModalProps {
  isOpen: boolean;
  onClose: () => void;
  release: Release | null;
}

export const ChangelogModal: React.FC<ChangelogModalProps> = ({ isOpen, onClose, release }) => {
  if (!isOpen || !release) return null;

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', { 
      year: 'numeric', 
      month: 'short', 
      day: 'numeric' 
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
      <div className="relative w-full max-w-2xl max-h-[90vh] bg-white/[0.03] backdrop-blur-md border border-white/5 rounded-3xl shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-white/5">
          <div className="flex items-center space-x-4">
            <div className="w-12 h-12 bg-echo-primary/10 border border-echo-primary/20 rounded-2xl flex items-center justify-center">
              <Wrench className="w-5 h-5 text-echo-primary" />
            </div>
            <div>
              <h2 className="text-xl font-bold text-white tracking-tight">
                Version {release.version}
              </h2>
              <p className="text-xs text-echo-muted">
                {formatDate(release.date)}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="w-10 h-10 flex items-center justify-center hover:bg-white/5 rounded-xl transition-colors"
          >
            <X className="w-5 h-5 text-echo-muted hover:text-white" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 overflow-y-auto max-h-[calc(90vh-120px)]">
          <div className="space-y-6">
            {/* Added */}
            {release.added && release.added.length > 0 && (
              <div className="bg-white/[0.02] rounded-2xl p-5 border border-white/5">
                <div className="flex items-center space-x-3 mb-4">
                  <div className="w-8 h-8 bg-green-500/10 border border-green-500/20 rounded-xl flex items-center justify-center">
                    <Plus className="w-4 h-4 text-green-500" />
                  </div>
                  <h3 className="text-sm font-bold text-white">Added</h3>
                </div>
                <ul className="space-y-2 ml-1">
                  {release.added.map((item, idx) => (
                    <li key={idx} className="text-sm text-echo-text font-sans flex items-start">
                      <span className="text-green-500 mr-3 mt-1">•</span>
                      <span>{item}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {/* Changed */}
            {release.changed && release.changed.length > 0 && (
              <div className="bg-white/[0.02] rounded-2xl p-5 border border-white/5">
                <div className="flex items-center space-x-3 mb-4">
                  <div className="w-8 h-8 bg-blue-500/10 border border-blue-500/20 rounded-xl flex items-center justify-center">
                    <Wrench className="w-4 h-4 text-blue-500" />
                  </div>
                  <h3 className="text-sm font-bold text-white">Changed</h3>
                </div>
                <ul className="space-y-2 ml-1">
                  {release.changed.map((item, idx) => (
                    <li key={idx} className="text-sm text-echo-text font-sans flex items-start">
                      <span className="text-blue-500 mr-3 mt-1">•</span>
                      <span>{item}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {/* Fixed */}
            {release.fixed && release.fixed.length > 0 && (
              <div className="bg-white/[0.02] rounded-2xl p-5 border border-white/5">
                <div className="flex items-center space-x-3 mb-4">
                  <div className="w-8 h-8 bg-orange-500/10 border border-orange-500/20 rounded-xl flex items-center justify-center">
                    <Minus className="w-4 h-4 text-orange-500" />
                  </div>
                  <h3 className="text-sm font-bold text-white">Fixed</h3>
                </div>
                <ul className="space-y-2 ml-1">
                  {release.fixed.map((item, idx) => (
                    <li key={idx} className="text-sm text-echo-text font-sans flex items-start">
                      <span className="text-orange-500 mr-3 mt-1">•</span>
                      <span>{item}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {(!release.added || release.added.length === 0) &&
             (!release.changed || release.changed.length === 0) &&
             (!release.fixed || release.fixed.length === 0) && (
              <div className="text-center py-12 bg-white/[0.02] rounded-2xl border border-white/5">
                <p className="text-echo-muted text-sm">No changelog entries for this release.</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};


