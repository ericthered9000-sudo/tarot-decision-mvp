package com.keystonedigital.decisiontarot.plugins.billing;

import android.app.Activity;
import android.util.Log;
import com.android.billingclient.api.*;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Direct Google Play Billing bridge.
 *
 * v5.1.0 change: every query path now covers BOTH product types.
 *   - subscriptions : BillingClient.ProductType.SUBS     (tarot_monthly, yearly_base)
 *   - one-time      : BillingClient.ProductType.INAPP    (tarot_lifetime)
 * Before this, a one-time product could never be found, purchased, restored or counted
 * toward premium. Entitlement decisions are delegated to BillingPremium (pure, unit-tested).
 */
@CapacitorPlugin(name = "GooglePlayBilling")
public class GooglePlayBillingPlugin extends Plugin {

    private static final String TAG = "GooglePlayBilling";
    private BillingClient billingClient = null;
    private Map<String, PluginCall> purchaseCalls = new HashMap<String, PluginCall>();
    private boolean connectionReady = false;
    private boolean connecting = false;
    private final List<Runnable> pendingTasks = new ArrayList<Runnable>();

    @PluginMethod
    public void init(PluginCall call) {
        try {
            if (connectionReady) {
                call.resolve();
                return;
            }
            if (connecting) {
                // Queue this call to resolve when connection completes
                pendingTasks.add(new Runnable() {
                    @Override
                    public void run() {
                        call.resolve();
                    }
                });
                return;
            }
            connecting = true;
            Activity activity = getActivity();
            if (activity == null) {
                call.reject("No activity available");
                connecting = false;
                return;
            }
            billingClient = BillingClient.newBuilder(activity)
                .setListener(new PurchasesUpdatedListener() {
                    @Override
                    public void onPurchasesUpdated(BillingResult r, List<Purchase> p) {
                        handlePurchaseUpdate(r, p);
                    }
                })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
            billingClient.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(BillingResult r) {
                    Log.d(TAG, "Connected: " + r.getResponseCode());
                    connectionReady = (r.getResponseCode() == BillingClient.BillingResponseCode.OK);
                    connecting = false;
                    if (connectionReady) {
                        // Run tasks queued while connecting
                        for (Runnable task : pendingTasks) {
                            task.run();
                        }
                        // Also resolve the original call that started this
                        // (it might have been queued too, but if not, it's already resolved)
                        pendingTasks.clear();
                        call.resolve();
                    } else {
                        call.reject("Billing connection failed: " + r.getDebugMessage());
                    }
                }
                @Override
                public void onBillingServiceDisconnected() {
                    Log.d(TAG, "Disconnected");
                    connectionReady = false;
                }
            });
        } catch (Exception e) {
            connecting = false;
            call.reject("Init error: " + e.getMessage());
        }
    }

    private void handlePurchaseUpdate(BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            if (purchases != null) {
                for (Purchase purch : purchases) {
                    List<String> pids = purch.getProducts();
                    if (!pids.isEmpty()) {
                        String prodId = pids.get(0);
                        PluginCall pc = purchaseCalls.remove(prodId);
                        if (pc != null) {
                            JSObject r = new JSObject();
                            r.put("success", true);
                            r.put("productId", prodId);
                            r.put("purchaseToken", purch.getPurchaseToken());
                            r.put("orderId", purch.getOrderId() != null ? purch.getOrderId() : "");
                            r.put("isAutoRenewing", purch.isAutoRenewing());
                            pc.resolve(r);
                        }
                    }
                }
                ackPurchases(purchases);
            }
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            for (PluginCall c : purchaseCalls.values()) {
                JSObject r = new JSObject();
                r.put("success", false);
                r.put("cancelled", true);
                c.resolve(r);
            }
            purchaseCalls.clear();
        } else {
            for (PluginCall c : purchaseCalls.values()) {
                c.reject("Purchase failed");
            }
            purchaseCalls.clear();
        }
    }

    /**
     * Acknowledge every PURCHASED, unacknowledged purchase. Type-agnostic on purpose:
     * SUBSCRIPTIONS and INAPP (tarot_lifetime) both go through here, so a one-time product
     * is never left unacknowledged (Play auto-refunds those after 3 days).
     */
    private void ackPurchases(List<Purchase> purchases) {
        if (billingClient == null || purchases == null) return;
        for (Purchase p : purchases) {
            if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED && !p.isAcknowledged()) {
                Log.d(TAG, "Acknowledging " + p.getProducts() + " (ack fires for SUBS and INAPP)");
                billingClient.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(),
                    new AcknowledgePurchaseResponseListener() {
                        @Override
                        public void onAcknowledgePurchaseResponse(BillingResult r) {
                            Log.d(TAG, "Ack: " + r.getResponseCode());
                        }
                    });
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Runs one product-details query for a single product type. */
    private void queryDetailsForType(List<String> ids, String productType, ProductDetailsResponseListener listener) {
        List<QueryProductDetailsParams.Product> plist = new ArrayList<QueryProductDetailsParams.Product>();
        for (String id : ids) {
            plist.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(productType)
                .build());
        }
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(plist).build(), listener);
    }

    private void addProductJson(JSArray out, ProductDetails d, String queriedType) {
        JSObject p = new JSObject();
        p.put("productId", d.getProductId());
        p.put("title", d.getTitle());
        p.put("description", d.getDescription());
        p.put("name", d.getName());
        p.put("type", d.getProductType());
        // Which query produced it — lets the web layer tell a sub from a one-time product
        // even when the store response is sparse.
        p.put("queriedType", queriedType);
        List<ProductDetails.SubscriptionOfferDetails> offers = d.getSubscriptionOfferDetails();
        if (offers != null && offers.size() > 0) {
            ProductDetails.SubscriptionOfferDetails o = offers.get(0);
            p.put("basePlanId", o.getBasePlanId());
            p.put("offerToken", o.getOfferToken());
            List<ProductDetails.PricingPhase> phases = o.getPricingPhases().getPricingPhaseList();
            if (phases != null && phases.size() > 0) {
                ProductDetails.PricingPhase pp = phases.get(0);
                p.put("price", pp.getFormattedPrice());
                p.put("priceAmountMicros", pp.getPriceAmountMicros());
                p.put("currencyCode", pp.getPriceCurrencyCode());
                p.put("billingPeriod", pp.getBillingPeriod());
            }
        } else if (d.getOneTimePurchaseOfferDetails() != null) {
            ProductDetails.OneTimePurchaseOfferDetails one = d.getOneTimePurchaseOfferDetails();
            p.put("price", one.getFormattedPrice());
            p.put("priceAmountMicros", one.getPriceAmountMicros());
            p.put("currencyCode", one.getPriceCurrencyCode());
            p.put("billingPeriod", "");
        }
        out.put(p);
    }

    private List<BillingPremium.Row> toRows(List<Purchase> purchases, String type) {
        List<BillingPremium.Row> rows = new ArrayList<BillingPremium.Row>();
        if (purchases == null) return rows;
        for (Purchase p : purchases) {
            List<String> prods = p.getProducts();
            rows.add(new BillingPremium.Row(
                prods.isEmpty() ? "" : prods.get(0),
                type,
                p.getPurchaseState()));
        }
        return rows;
    }

    private JSArray rowsToJson(List<BillingPremium.Row> rows) {
        JSArray arr = new JSArray();
        for (BillingPremium.Row r : rows) {
            JSObject item = new JSObject();
            item.put("productId", r.productId);
            item.put("productType", r.productType);
            item.put("purchaseState", r.state);
            arr.put(item);
        }
        return arr;
    }

    // ------------------------------------------------------------------ plugin methods

    @PluginMethod
    public void queryProducts(PluginCall call) {
        try {
            if (!connectionReady || billingClient == null) {
                call.reject("Billing not ready");
                return;
            }
            final PluginCall theCall = call;
            List<Object> rawIds = call.getArray("productIds").toList();
            if (rawIds == null || rawIds.isEmpty()) {
                call.reject("productIds required");
                return;
            }
            final List<String> ids = new ArrayList<String>();
            for (Object id : rawIds) ids.add(id.toString());

            final JSArray products = new JSArray();
            final JSArray warnings = new JSArray();

            // SUBS first (tarot_monthly, yearly_base), then INAPP (tarot_lifetime).
            queryDetailsForType(ids, BillingClient.ProductType.SUBS, new ProductDetailsResponseListener() {
                @Override
                public void onProductDetailsResponse(BillingResult subsResult, QueryProductDetailsResult subsQr) {
                    if (subsResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        for (ProductDetails d : subsQr.getProductDetailsList()) {
                            addProductJson(products, d, BillingPremium.TYPE_SUBS);
                        }
                    } else {
                        warnings.put("subs:" + subsResult.getResponseCode());
                    }
                    // One-time products — same ids, INAPP type.
                    queryDetailsForType(ids, BillingClient.ProductType.INAPP, new ProductDetailsResponseListener() {
                        @Override
                        public void onProductDetailsResponse(BillingResult inappResult, QueryProductDetailsResult inappQr) {
                            if (inappResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                for (ProductDetails d : inappQr.getProductDetailsList()) {
                                    addProductJson(products, d, BillingPremium.TYPE_INAPP);
                                }
                            } else {
                                warnings.put("inapp:" + inappResult.getResponseCode());
                            }
                            if (products.length() == 0 && warnings.length() == 2) {
                                // Both types failed — genuine error, surface it.
                                theCall.reject("Query failed for both product types: " + warnings);
                                return;
                            }
                            JSObject out = new JSObject();
                            out.put("products", products);
                            out.put("warnings", warnings);
                            out.put("queriedTypes", BillingPremium.TYPE_SUBS + "," + BillingPremium.TYPE_INAPP);
                            Log.d(TAG, "queryProducts " + ids + " -> " + products.length() + " hit(s)");
                            theCall.resolve(out);
                        }
                    });
                }
            });
        } catch (Exception e) {
            call.reject("Error: " + e.getMessage());
        }
    }

    @PluginMethod
    public void purchase(PluginCall call) {
        try {
            if (!connectionReady || billingClient == null) {
                call.reject("Billing not ready");
                return;
            }
            final String productId = call.getString("productId");
            if (productId == null) {
                call.reject("productId required");
                return;
            }
            final String offerToken = call.getString("offerToken");
            final Activity activity = getActivity();
            if (activity == null) {
                call.reject("No activity");
                return;
            }
            final PluginCall theCall = call;
            final List<String> one = new ArrayList<String>();
            one.add(productId);

            // Try SUBS first; fall back to INAPP for one-time products (tarot_lifetime).
            queryDetailsForType(one, BillingClient.ProductType.SUBS, new ProductDetailsResponseListener() {
                @Override
                public void onProductDetailsResponse(BillingResult subsResult, QueryProductDetailsResult subsQr) {
                    List<ProductDetails> subsList = subsResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                        ? subsQr.getProductDetailsList()
                        : new ArrayList<ProductDetails>();
                    if (!subsList.isEmpty()) {
                        launchFlow(subsList.get(0), BillingPremium.TYPE_SUBS, offerToken, activity, theCall);
                        return;
                    }
                    queryDetailsForType(one, BillingClient.ProductType.INAPP, new ProductDetailsResponseListener() {
                        @Override
                        public void onProductDetailsResponse(BillingResult inappResult, QueryProductDetailsResult inappQr) {
                            List<ProductDetails> inappList = inappResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                                ? inappQr.getProductDetailsList()
                                : new ArrayList<ProductDetails>();
                            if (!inappList.isEmpty()) {
                                launchFlow(inappList.get(0), BillingPremium.TYPE_INAPP, null, activity, theCall);
                                return;
                            }
                            theCall.reject("Not found: " + productId
                                + " (subs=" + subsResult.getResponseCode()
                                + ", inapp=" + inappResult.getResponseCode() + ")");
                        }
                    });
                }
            });
        } catch (Exception e) {
            call.reject("Error: " + e.getMessage());
        }
    }

    /** Launches the Play purchase sheet once the product details are known. */
    private void launchFlow(ProductDetails details, String type, String offerToken,
                            Activity activity, PluginCall call) {
        try {
            BillingFlowParams.ProductDetailsParams.Builder pdp =
                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details);
            if (BillingPremium.TYPE_SUBS.equals(type)) {
                List<ProductDetails.SubscriptionOfferDetails> offers = details.getSubscriptionOfferDetails();
                if (offers == null || offers.isEmpty()) {
                    call.reject("No offers");
                    return;
                }
                ProductDetails.SubscriptionOfferDetails target = offers.get(0);
                if (offerToken != null) {
                    for (ProductDetails.SubscriptionOfferDetails o : offers) {
                        if (o.getOfferToken().equals(offerToken)) {
                            target = o;
                            break;
                        }
                    }
                }
                pdp.setOfferToken(target.getOfferToken());
            }
            // INAPP: no offer token — the one-time purchase sheet is built from the details alone.
            List<BillingFlowParams.ProductDetailsParams> pparams = new ArrayList<BillingFlowParams.ProductDetailsParams>();
            pparams.add(pdp.build());
            String productId = details.getProductId();
            purchaseCalls.put(productId, call);
            Log.d(TAG, "Launching billing flow for " + productId + " as " + type);
            BillingResult lr = billingClient.launchBillingFlow(activity,
                BillingFlowParams.newBuilder().setProductDetailsParamsList(pparams).build());
            if (lr.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                purchaseCalls.remove(productId);
                call.reject("Launch failed: " + lr.getResponseCode());
            }
        } catch (Exception e) {
            call.reject("Launch error: " + e.getMessage());
        }
    }

    @PluginMethod
    public void isPremium(PluginCall call) {
        if (!connectionReady || billingClient == null) {
            call.reject("Billing not ready");
            return;
        }
        final PluginCall theCall = call;
        // Query SUBS and INAPP; a purchase in EITHER type means premium.
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
            new PurchasesResponseListener() {
                @Override
                public void onQueryPurchasesResponse(BillingResult subsResult, List<Purchase> subsPurchases) {
                    if (subsResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        ackPurchases(subsPurchases);
                    }
                    billingClient.queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                        new PurchasesResponseListener() {
                            @Override
                            public void onQueryPurchasesResponse(BillingResult inappResult, List<Purchase> inappPurchases) {
                                if (inappResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                    ackPurchases(inappPurchases);
                                }
                                List<BillingPremium.Row> rows = BillingPremium.merge(
                                    toRows(subsPurchases, BillingPremium.TYPE_SUBS),
                                    toRows(inappPurchases, BillingPremium.TYPE_INAPP));
                                boolean premium = BillingPremium.isPremium(rows);
                                JSObject r = new JSObject();
                                r.put("isPremium", premium);
                                r.put("purchases", rowsToJson(BillingPremium.purchasedRows(rows)));
                                r.put("subsResponseCode", subsResult.getResponseCode());
                                r.put("inappResponseCode", inappResult.getResponseCode());
                                Log.d(TAG, "isPremium=" + premium + " rows=" + rows);
                                theCall.resolve(r);
                            }
                        });
                }
            });
    }

    @PluginMethod
    public void restorePurchases(PluginCall call) {
        if (!connectionReady || billingClient == null) {
            call.reject("Billing not ready");
            return;
        }
        final PluginCall theCall = call;
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
            new PurchasesResponseListener() {
                @Override
                public void onQueryPurchasesResponse(BillingResult subsResult,
                                                     final List<Purchase> subsPurchases) {
                    billingClient.queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                        new PurchasesResponseListener() {
                            @Override
                            public void onQueryPurchasesResponse(BillingResult inappResult, List<Purchase> inappPurchases) {
                                if (subsResult.getResponseCode() != BillingClient.BillingResponseCode.OK
                                    && inappResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                                    theCall.reject("Query failed (subs=" + subsResult.getResponseCode()
                                        + ", inapp=" + inappResult.getResponseCode() + ")");
                                    return;
                                }
                                ackPurchases(subsPurchases);
                                ackPurchases(inappPurchases);

                                JSArray found = new JSArray();
                                List<Purchase> all = new ArrayList<Purchase>();
                                if (subsPurchases != null) all.addAll(subsPurchases);
                                if (inappPurchases != null) all.addAll(inappPurchases);

                                for (Purchase p : all) {
                                    if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
                                    List<String> prods = p.getProducts();
                                    JSObject item = new JSObject();
                                    item.put("productId", prods.isEmpty() ? "" : prods.get(0));
                                    item.put("purchaseToken", p.getPurchaseToken());
                                    item.put("orderId", p.getOrderId() != null ? p.getOrderId() : "");
                                    item.put("purchaseTime", p.getPurchaseTime());
                                    item.put("isAutoRenewing", p.isAutoRenewing());
                                    found.put(item);
                                }
                                JSObject r = new JSObject();
                                r.put("products", found);
                                r.put("success", true);
                                theCall.resolve(r);
                            }
                        });
                }
            });
    }

    @Override
    protected void handleOnDestroy() {
        if (billingClient != null) {
            billingClient.endConnection();
            billingClient = null;
        }
        purchaseCalls.clear();
        connectionReady = false;
        connecting = false;
    }
}
