import React, { useState } from 'react';
import { Card, Button, Input } from './UI';
import { X, Key, CheckCircle, AlertCircle, Loader2 } from 'lucide-react';
import { keyApi } from '../api';

interface KeyModalProps {
  isOpen: boolean;
  onClose: () => void;
  onRedeemSuccess?: () => void;
}

export const KeyModal: React.FC<KeyModalProps> = ({ isOpen, onClose, onRedeemSuccess }) => {
  const [key, setKey] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleRedeem = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setLoading(true);

    try {
      const response = await keyApi.redeem(key.trim());
      
      if (response.success) {
        setSuccess(`License key redeemed! Plan: ${response.plan?.toUpperCase() || 'PREMIUM'}`);
        setKey('');
        setTimeout(() => {
          onRedeemSuccess?.();
          onClose();
          setSuccess(null);
        }, 2000);
      } else {
        setError(response.message || 'Failed to redeem license key');
      }
    } catch (err) {
      setError('Network error occurred');
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    setKey('');
    setError(null);
    setSuccess(null);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
      <Card className="w-full max-w-md relative border-echo-border">
        <div className="flex items-center justify-between border-b border-echo-border p-4 bg-echo-surface">
          <div className="flex items-center space-x-2">
            <Key className="w-5 h-5 text-echo-primary" />
            <h2 className="text-lg font-bold text-white uppercase tracking-tight">Redeem License Key</h2>
          </div>
          <button
            onClick={handleClose}
            className="text-echo-muted hover:text-echo-white transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6">
          {error && (
            <div className="mb-4 p-3 bg-red-500/10 border border-red-500/50 text-red-400 text-xs font-sans flex items-center">
              <AlertCircle className="w-4 h-4 mr-2 flex-shrink-0" />
              {error}
            </div>
          )}

          {success && (
            <div className="mb-4 p-3 bg-echo-primary/10 border border-echo-primary/50 text-echo-primary text-xs font-sans flex items-center">
              <CheckCircle className="w-4 h-4 mr-2 flex-shrink-0" />
              {success}
            </div>
          )}

          <form onSubmit={handleRedeem} className="space-y-4">
            <Input
              label="License Key"
              placeholder="ECHO-XXXX-XXXX-XXXX-XXXX"
              value={key}
              onChange={(e) => setKey(e.target.value.toUpperCase())}
              icon={<Key className="w-4 h-4" />}
              required
              disabled={loading}
              maxLength={24}
            />

            <div className="pt-2">
              <Button
                type="submit"
                fullWidth
                disabled={loading || !key.trim()}
                className="h-11"
              >
                {loading ? (
                  <>
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    REDEEMING...
                  </>
                ) : (
                  <>
                    <Key className="w-4 h-4 mr-2" />
                    REDEEM KEY
                  </>
                )}
              </Button>
            </div>
          </form>

          <div className="mt-4 pt-4 border-t border-echo-border">
            <p className="text-[10px] font-sans text-echo-muted text-center">
              Enter your license key to activate your premium plan
            </p>
          </div>
        </div>
      </Card>
    </div>
  );
};


