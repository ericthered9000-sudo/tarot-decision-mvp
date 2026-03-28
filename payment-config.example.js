// Payment Configuration - RevenueCat + Stripe
// Copy this to payment-config.js and fill in your keys

export const PAYMENT_CONFIG = {
  // RevenueCat Web SDK (handles payments without app store 30% cut)
  revenueCat: {
    apiKey: 'YOUR_REVENUECAT_PUBLIC_API_KEY',
    appId: 'com.keystonedigital.tarot'
  },
  
  // Product configuration
  products: {
    oneTimePurchase: {
      id: 'tarot_lifetime_access',
      price: 6.99,
      currency: 'USD',
      name: 'Lifetime Access',
      description: 'One-time purchase - unlimited readings forever'
    }
  },
  
  // Feature gates (what's free vs paid)
  features: {
    free: {
      dailyReadings: 1,
      spreadTypes: ['three-card'],
      saveHistory: false
    },
    paid: {
      dailyReadings: -1, // unlimited
      spreadTypes: ['three-card', 'celtic-cross', 'relationship'],
      saveHistory: true
    }
  }
};

// RevenueCat setup helper
export async function initRevenueCat() {
  if (typeof window.Purchases !== 'undefined') {
    await window.Purchases.configure({
      apiKey: PAYMENT_CONFIG.revenueCat.apiKey,
      appUserId: undefined // Let RevenueCat generate anonymous ID
    });
    console.log('✅ RevenueCat initialized');
  }
}

// Check if user has purchased
export async function hasPurchased() {
  if (typeof window.Purchases === 'undefined') return false;
  
  try {
    const customerInfo = await window.Purchases.getCustomerInfo();
    return customerInfo.entitlements.active['lifetime_access'] !== undefined;
  } catch (err) {
    console.error('Error checking purchase:', err);
    return false;
  }
}

// Purchase flow
export async function purchaseLifetimeAccess() {
  if (typeof window.Purchases === 'undefined') {
    alert('Payment system not initialized');
    return false;
  }
  
  try {
    const offerings = await window.Purchases.getOfferings();
    const product = offerings.current?.availablePackages.find(
      p => p.identifier === PAYMENT_CONFIG.products.oneTimePurchase.id
    );
    
    if (!product) {
      alert('Product not found');
      return false;
    }
    
    const purchase = await window.Purchases.purchaseProduct(product);
    return purchase.customerInfo.entitlements.active['lifetime_access'] !== undefined;
  } catch (err) {
    if (err.userCanceled) {
      console.log('User canceled purchase');
      return false;
    }
    console.error('Purchase error:', err);
    alert('Purchase failed. Please try again.');
    return false;
  }
}
