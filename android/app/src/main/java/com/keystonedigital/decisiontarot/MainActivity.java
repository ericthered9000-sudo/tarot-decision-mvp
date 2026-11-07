package com.keystonedigital.decisiontarot;

import com.getcapacitor.BridgeActivity;
import com.keystonedigital.decisiontarot.plugins.billing.GooglePlayBillingPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        // Capacitor 5+ creates the bridge inside super.onCreate(), so plugins MUST be
        // registered BEFORE it — otherwise the JS side sees "GooglePlayBilling plugin
        // not found" and every purchase path silently dies (v5.1.0 regression found on
        // device 2026-10-27: logcat "Billing init failed: GooglePlayBilling plugin not found").
        registerPlugin(GooglePlayBillingPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
