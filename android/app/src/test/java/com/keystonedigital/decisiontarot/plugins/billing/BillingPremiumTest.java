package com.keystonedigital.decisiontarot.plugins.billing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for the product-type-agnostic entitlement logic introduced in v5.1.0.
 *
 * These run on a plain JVM (./gradlew testDebugUnitTest) with no Play connection, which is
 * how "isPremium responds to an INAPP purchase row" is verified without a Play-signed build:
 * the rows below are exactly what the plugin builds from a query result.
 */
public class BillingPremiumTest {

    private static BillingPremium.Row row(String id, String type, int state) {
        return new BillingPremium.Row(id, type, state);
    }

    @Test
    public void simulatedInappLifetimePurchaseMakesUserPremium() {
        // What isPremium() gets back for a user who bought tarot_lifetime only:
        // SUBS query empty, INAPP query returns one PURCHASED row.
        List<BillingPremium.Row> subsRows = new ArrayList<BillingPremium.Row>();
        List<BillingPremium.Row> inappRows = new ArrayList<BillingPremium.Row>();
        inappRows.add(row("tarot_lifetime", BillingPremium.TYPE_INAPP, BillingPremium.STATE_PURCHASED));

        List<BillingPremium.Row> merged = BillingPremium.merge(subsRows, inappRows);

        assertTrue("a PURCHASED INAPP row must grant premium", BillingPremium.isPremium(merged));
        assertEquals(1, BillingPremium.purchasedRows(merged).size());
        assertEquals("tarot_lifetime", BillingPremium.purchasedRows(merged).get(0).productId);
        // Regression guard: the old SUBS-only behaviour would have decided this here.
        assertFalse("SUBS-only rows (empty) must not be premium", BillingPremium.isPremium(subsRows));
    }

    @Test
    public void yearlyBaseSubscriptionStillGrantsPremium() {
        List<BillingPremium.Row> subsRows = new ArrayList<BillingPremium.Row>();
        subsRows.add(row("yearly_base", BillingPremium.TYPE_SUBS, BillingPremium.STATE_PURCHASED));

        assertTrue(BillingPremium.isPremium(BillingPremium.merge(subsRows, new ArrayList<BillingPremium.Row>())));
    }

    @Test
    public void pendingPurchaseIsNotPremiumYet() {
        List<BillingPremium.Row> inappRows = new ArrayList<BillingPremium.Row>();
        inappRows.add(row("tarot_lifetime", BillingPremium.TYPE_INAPP, 2)); // PurchaseState.PENDING

        assertFalse(BillingPremium.isPremium(inappRows));
        assertTrue(BillingPremium.purchasedRows(inappRows).isEmpty());
    }

    @Test
    public void mergeKeepsBothTypesAndIgnoresNulls() {
        List<BillingPremium.Row> subs = Arrays.asList(row("tarot_monthly", BillingPremium.TYPE_SUBS, BillingPremium.STATE_PURCHASED));
        List<BillingPremium.Row> inapp = Arrays.asList(row("tarot_lifetime", BillingPremium.TYPE_INAPP, BillingPremium.STATE_PURCHASED));

        List<BillingPremium.Row> merged = BillingPremium.merge(subs, inapp);
        assertEquals(2, merged.size());
        assertEquals(2, BillingPremium.purchasedRows(merged).size());

        assertEquals(0, BillingPremium.merge(null, null).size());
        assertFalse(BillingPremium.isPremium(null));
    }

    @Test
    public void productTypeConstantsMatchPlayBillingApi() {
        // These literals must stay in lockstep with BillingClient.ProductType.* in the plugin.
        assertEquals("subs", BillingPremium.TYPE_SUBS);
        assertEquals("inapp", BillingPremium.TYPE_INAPP);
        assertEquals(1, BillingPremium.STATE_PURCHASED); // Purchase.PurchaseState.PURCHASED
    }
}
