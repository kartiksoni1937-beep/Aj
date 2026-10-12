import React, { useState, useEffect } from 'react';
import { Button } from './UI';
import { X, CreditCard, Infinity, Calendar, Check, Loader2 } from 'lucide-react';
import { paymentApi, oxapayApi } from '../api';

interface PaymentModalProps {
  isOpen: boolean;
  onClose: () => void;
  onPaymentSuccess?: () => void;
  hasSubscription?: boolean;
}

interface Prices {
  stripe: {
    lifetime: { amount: number; currency: string; formatted: string };
    monthly: { amount: number; currency: string; formatted: string };
  };
  oxapay: {
    lifetime: { amount: number; currency: string; formatted: string };
    monthly: { amount: number; currency: string; formatted: string };
  };
}

export const PaymentModal: React.FC<PaymentModalProps> = ({ isOpen, onClose, onPaymentSuccess, hasSubscription = false }) => {
  const [selectedPlan, setSelectedPlan] = useState<'lifetime' | 'monthly' | null>(null);
  const [selectedProvider, setSelectedProvider] = useState<'stripe' | 'oxapay'>('stripe');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [prices, setPrices] = useState<Prices | null>(null);
  const [pricesLoading, setPricesLoading] = useState(true);

  useEffect(() => {
    if (isOpen) {
      fetchPrices();
    }
  }, [isOpen]);

  const fetchPrices = async () => {
    setPricesLoading(true);
    try {
      const response = await paymentApi.getPrices();
      if (response.success && response.prices) {
        setPrices(response.prices);
      } else {
        setError('Failed to load prices');
      }
    } catch (err) {
      console.error('Error fetching prices:', err);
      setError('Failed to load prices');
    } finally {
      setPricesLoading(false);
    }
  };

  const getPrice = (plan: 'lifetime' | 'monthly') => {
    if (!prices) return { formatted: '...', currency: 'EUR' };
    const providerPrices = selectedProvider === 'stripe' ? prices.stripe : prices.oxapay;
    return {
      formatted: providerPrices[plan].formatted,
      currency: providerPrices[plan].currency.toUpperCase()
    };
  };

  if (!isOpen) return null;

  const handlePurchase = async (plan: 'lifetime' | 'monthly') => {
    setError(null);
    setLoading(true);

    try {
      let response;
      if (selectedProvider === 'stripe') {
        if (plan === 'lifetime') {
          response = await paymentApi.createLifetimeCheckout();
        } else {
          response = await paymentApi.createMonthlySubscription();
        }

        if (response.success && response.url) {
          // Redirect to Stripe Checkout
          window.location.href = response.url;
          return;
        } else {
          setError(response.message || 'Failed to create checkout session');
        }
      } else {
        // OxaPay
        if (plan === 'lifetime') {
          response = await oxapayApi.createLifetimeInvoice();
        } else {
          response = await oxapayApi.createMonthlyInvoice();
        }

        if (response.success && response.paymentUrl) {
          // Redirect to OxaPay payment page
          window.location.href = response.paymentUrl;
          return;
        } else {
          setError(response.message || 'Failed to create payment invoice');
        }
      }

      setLoading(false);
    } catch (err: any) {
      console.error('Purchase error:', err);
      setError(err.message || 'An unexpected error occurred. Please try again.');
      setLoading(false);
    }
  };

  const handleManageSubscription = async () => {
    setError(null);
    setLoading(true);

    try {
      const response = await paymentApi.getCustomerPortal();
      if (response.success && response.url) {
        window.location.href = response.url;
      } else {
        setError(response.message || 'Failed to open customer portal');
        setLoading(false);
      }
    } catch (err) {
      setError('An unexpected error occurred');
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-md flex items-center justify-center p-4 z-[100]">
      <div className="bg-white/[0.05] backdrop-blur-xl border-2 border-white/10 rounded-2xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        <div className="p-6 md:p-8">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-2xl font-bold text-white">Upgrade Your Plan</h2>
            <button
              onClick={onClose}
              className="text-echo-muted hover:text-white transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {error && (
            <div className="mb-4 p-4 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-sm">
              {error}
            </div>
          )}

          {/* Payment Provider Selection */}
          <div className="mb-6">
            <p className="text-sm font-semibold text-white mb-3">Payment Method:</p>
            <div className="flex gap-3">
              <button
                onClick={() => {
                  setSelectedProvider('stripe');
                  setError(null);
                }}
                className={`flex-1 p-4 rounded-xl border-2 transition-all ${
                  selectedProvider === 'stripe'
                    ? 'border-echo-primary bg-echo-primary/10 shadow-lg shadow-echo-primary/20'
                    : 'border-white/5 bg-white/[0.02] hover:border-white/10'
                }`}
              >
                <div className="flex items-center justify-center gap-2">
                  <CreditCard className={`w-5 h-5 ${selectedProvider === 'stripe' ? 'text-echo-primary' : 'text-echo-muted'}`} />
                  <span className={`text-sm font-medium ${selectedProvider === 'stripe' ? 'text-white' : 'text-echo-muted'}`}>Card (Stripe)</span>
                </div>
              </button>
              <button
                onClick={() => {
                  setSelectedProvider('oxapay');
                  setError(null);
                }}
                className={`flex-1 p-4 rounded-xl border-2 transition-all ${
                  selectedProvider === 'oxapay'
                    ? 'border-echo-primary bg-echo-primary/10 shadow-lg shadow-echo-primary/20'
                    : 'border-white/5 bg-white/[0.02] hover:border-white/10'
                }`}
              >
                <div className="flex items-center justify-center gap-2">
                  <CreditCard className={`w-5 h-5 ${selectedProvider === 'oxapay' ? 'text-echo-primary' : 'text-echo-muted'}`} />
                  <span className={`text-sm font-medium ${selectedProvider === 'oxapay' ? 'text-white' : 'text-echo-muted'}`}>Crypto (OxaPay)</span>
                </div>
              </button>
            </div>
          </div>

          <div className="grid md:grid-cols-2 gap-4 mb-6">
            {/* Monthly Plan */}
            <div
              className={`p-6 rounded-xl border-2 transition-all cursor-pointer relative ${
                selectedPlan === 'monthly'
                  ? 'border-echo-primary bg-echo-primary/10'
                  : 'border-white/5 bg-white/[0.02] hover:border-white/10'
              }`}
              onClick={() => setSelectedPlan('monthly')}
            >
              <div className="absolute top-4 left-4 px-2 py-1 bg-echo-primary/20 text-echo-primary text-xs font-bold rounded z-10">
                BEST VALUE
              </div>
              <div className="flex items-center justify-between mb-4 mt-6">
                <div className="flex items-center gap-2">
                  <Calendar className="w-5 h-5 text-echo-primary" />
                  <h3 className="text-lg font-bold text-white">Monthly</h3>
                </div>
                {selectedPlan === 'monthly' && (
                  <Check className="w-5 h-5 text-echo-primary flex-shrink-0" />
                )}
              </div>
              <div className="mb-4">
                {pricesLoading ? (
                  <div className="flex items-center gap-2">
                    <Loader2 className="w-6 h-6 text-echo-primary animate-spin" />
                    <span className="text-echo-muted">Loading...</span>
                  </div>
                ) : (
                  <div className="flex items-baseline gap-2">
                    <span className="text-3xl font-bold text-white">
                      €{getPrice('monthly').formatted}
                    </span>
                    <span className="text-echo-muted">/month</span>
                  </div>
                )}
              </div>
              <ul className="space-y-2 mb-6">
                <li className="flex items-center gap-2 text-sm text-echo-muted">
                  <Check className="w-4 h-4 text-echo-primary flex-shrink-0" />
                  <span>Premium features</span>
                </li>
                <li className="flex items-center gap-2 text-sm text-echo-muted">
                  <Check className="w-4 h-4 text-echo-primary flex-shrink-0" />
                  <span>Cancel anytime</span>
                </li>
                <li className="flex items-center gap-2 text-sm text-echo-muted">
                  {selectedProvider === 'oxapay' ? (
                    <X className="w-4 h-4 text-red-500 flex-shrink-0" />
                  ) : (
                    <Check className="w-4 h-4 text-echo-primary flex-shrink-0" />
                  )}
                  <span>Auto-renewal</span>
                </li>
              </ul>
              <Button
                fullWidth
                variant={selectedPlan === 'monthly' ? 'primary' : 'outline'}
                onClick={(e) => {
                  e.stopPropagation();
                  if (!selectedPlan) {
                    setSelectedPlan('monthly');
                  }
                  handlePurchase('monthly');
                }}
                disabled={loading || pricesLoading}
              >
                {loading ? (
                  <>
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    Processing...
                  </>
                ) : (
                  <>
                    <CreditCard className="w-4 h-4 mr-2" />
                    Subscribe
                  </>
                )}
              </Button>
            </div>

            {/* Lifetime Plan */}
            <div
              className={`p-6 rounded-xl border-2 transition-all cursor-pointer relative ${
                selectedPlan === 'lifetime'
                  ? 'border-echo-primary bg-echo-primary/10'
                  : 'border-white/5 bg-white/[0.02] hover:border-white/10'
              }`}
              onClick={() => setSelectedPlan('lifetime')}
            >
              <div className="flex items-center justify-between mb-4 mt-6">
                <div className="flex items-center gap-2">
                  <Infinity className="w-5 h-5 text-echo-primary" />
                  <h3 className="text-lg font-bold text-white">Lifetime</h3>
                </div>
                {selectedPlan === 'lifetime' && (
                  <Check className="w-5 h-5 text-echo-primary flex-shrink-0" />
                )}
              </div>
              <div className="mb-4">
                {pricesLoading ? (
                  <div className="flex items-center gap-2">
                    <Loader2 className="w-6 h-6 text-echo-primary animate-spin" />
                    <span className="text-echo-muted">Loading...</span>
                  </div>
                ) : (
                  <div className="flex items-baseline gap-2">
                    <span className="text-3xl font-bold text-white">
                      €{getPrice('lifetime').formatted}
                    </span>
                    <span className="text-echo-muted">one-time</span>
                  </div>
                )}
              </div>
              <ul className="space-y-2 mb-6">
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
                variant={selectedPlan === 'lifetime' ? 'primary' : 'outline'}
                onClick={(e) => {
                  e.stopPropagation();
                  if (!selectedPlan) {
                    setSelectedPlan('lifetime');
                  }
                  handlePurchase('lifetime');
                }}
                disabled={loading || pricesLoading}
              >
                {loading ? (
                  <>
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    Processing...
                  </>
                ) : (
                  <>
                    <CreditCard className="w-4 h-4 mr-2" />
                    Purchase
                  </>
                )}
              </Button>
            </div>
          </div>

          <div className="pt-4 border-t border-white/5">
            <p className="text-xs text-echo-muted text-center mb-4">
              {selectedProvider === 'stripe' 
                ? 'Secure payment powered by Stripe. Your payment information is encrypted and secure.'
                : 'Secure cryptocurrency payment powered by OxaPay. Supports multiple cryptocurrencies.'}
            </p>
            <div className="flex gap-3">
              <Button
                fullWidth
                variant="outline"
                onClick={onClose}
                disabled={loading}
              >
                Cancel
              </Button>
              {hasSubscription && (
                <Button
                  fullWidth
                  variant="secondary"
                  onClick={handleManageSubscription}
                  disabled={loading}
                >
                  Manage Subscription
                </Button>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

