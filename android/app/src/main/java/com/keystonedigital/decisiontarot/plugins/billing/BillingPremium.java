package com.keystonedigital.decisiontarot.plugins.billing;

import java.util.ArrayList;
import java.util.List;

/**
 * Product-type-agnostic billing decision logic.
 *
 * Why this class exists: GooglePlayBillingPlugin used to hardcode
 * {@code BillingClient.ProductType.SUBS} in all four query paths, so one-time (INAPP)
 * products — e.g. {@code tarot_lifetime} — were invisible to queryProducts(), isPremium()
 * and restorePurchases(). Entitlement now comes from querying BOTH product types and
 * merging the rows; the merge/decision half lives here.
 *
 * Deliberately pure Java (no android.* / billing imports) so it can be unit-tested on a
 * plain JVM without a Play connection:
 * see android/app/src/test/java/.../BillingPremiumTest.java
 */
final class BillingPremium {

    /** Mirrors BillingClient.ProductType.SUBS. */
    static final String TYPE_SUBS = "subs";
    /** Mirrors BillingClient.ProductType.INAPP. */
    static final String TYPE_INAPP = "inapp";
    /** Mirrors Purchase.PurchaseState.PURCHASED. */
    static final int STATE_PURCHASED = 1;

    private BillingPremium() {}

    /** One purchase row, reduced to the fields entitlement depends on. */
    static final class Row {
        final String productId;
        final String productType; // TYPE_SUBS | TYPE_INAPP
        final int state;          // Purchase.PurchaseState.*

        Row(String productId, String productType, int state) {
            this.productId = productId == null ? "" : productId;
            this.productType = productType == null ? "" : productType;
            this.state = state;
        }

        @Override
        public String toString() {
            return productId + "(" + productType + ",state=" + state + ")";
        }
    }

    /** Rows whose purchase state is PURCHASED, across every product type. */
    static List<Row> purchasedRows(List<Row> rows) {
        List<Row> out = new ArrayList<Row>();
        if (rows == null) return out;
        for (Row r : rows) {
            if (r != null && r.state == STATE_PURCHASED) out.add(r);
        }
        return out;
    }

    /**
     * Premium = the user owns ANY purchased product, subscription or one-time.
     * A pending purchase (e.g. slow card) is NOT premium yet.
     */
    static boolean isPremium(List<Row> rows) {
        return !purchasedRows(rows).isEmpty();
    }

    /** Merges the per-type query results into one list (SUBS rows first, then INAPP). */
    static List<Row> merge(List<Row> subsRows, List<Row> inappRows) {
        List<Row> out = new ArrayList<Row>();
        if (subsRows != null) out.addAll(subsRows);
        if (inappRows != null) out.addAll(inappRows);
        return out;
    }
}
