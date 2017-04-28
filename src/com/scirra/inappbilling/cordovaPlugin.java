package com.scirra.inappbilling;

import com.scirra.inappbilling.util.IabBroadcastReceiver;
import com.scirra.inappbilling.util.IabBroadcastReceiver.IabBroadcastListener;
import com.scirra.inappbilling.util.IabHelper;
import com.scirra.inappbilling.util.IabHelper.IabAsyncInProgressException;
import com.scirra.inappbilling.util.IabResult;
import com.scirra.inappbilling.util.Inventory;
import com.scirra.inappbilling.util.Purchase;

import org.apache.cordova.CorddovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class InAppBilling extends CorddovaPlugin
{
	// The helper object
    private IabHelper mHelper;
    // Provides purchase notification while this app is running
    private IabBroadcastReceiver mBroadcastReceiver;

    private Inventory mInventory;

	private static final int RC_REQUEST = 10001;
	
	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException
	{
		switch (action) {
			case "init":
				init(args.getString(0), args.getBoolean(1), callbackContext);
				break;
            case "refresh":
                refresh(callbackContext);
                break;
			case "purchase":
				purchase();
				break;
			case "consume":
				consume();
				break;
			case "getPurchase":
				getPurchase();
				break;
			default:
				callbackContext.error("Invalid action");
		}
		
		return false;
	}

	void init(String publicKey, boolean debug, CallbackContext callbackContext)
	{
		mHelper = new IabHelper(this, publicKey);
		mHelper.enableDebugLogging(debug);
		mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
			public void onIabSetupFinished(IabResult result) {

				if (mHelper == null)
					return;
					
				Log.d(TAG, "Setup finished.");

				if (!result.isSuccess()) {
					callbackContext.error("Failed to initialise: " + result.getMessage());
					return;
				}

				// It's generally advised that creating a BroadcastReciever inside an Activtity
                // is a bad idea as it will only exist for the same duration as the Activity.
                // However, in this situation we are using it purely to notify ourselves DURING
                // the Activity lifespan of changes to the inventory. So it's perfectly legit.

				mBroadcastReceiver = new IabBroadcastReceiver(MainActivity.this);
				IntentFilter broadcastFilter = new IntentFilter(IabBroadcastReceiver.ACTION);
				registerReceiver(mBroadcastReceiver, broadcastFilter);

				Log.d(TAG, "Setup successful. Querying inventory.");
				try {
					refresh(callbackContext);
				} catch (IabAsyncInProgressException e) {
					callbackContext.error("failed to initialise " + result);
					complain("Error querying inventory. Another async operation in progress.");
				}
			}
		});
	}
	
	void refresh(CallbackContext callbackContext)
	{
        try
        {
            mHelper.queryInventoryAsync(new IabHelper.QueryInventoryFinishedListener() {
                public void onQueryInventoryFinished(IabResult result, Inventory inventory) {

                    if (mHelper == null)
                        return;

                    if (result.isFailure()) {
                        callbackContext.error("Failed to query inventory: " + result);
                        return;
                    }

                    mInventory = inventory;

                    if (callbackContext != null)
                    {
                        callbackContext.success();
                    }
                }
            });
        }
        catch (IabAsyncInProgressException e)
        {
            if (callbackContext != null)
                callbackContext.error("Failed querying inventory: async is busy.");
            else
                throw e;
        }
	}

	void refresh()
	{
		refresh(null);
	}

	void purchase(String productSKU, String payload, CallbackContext callbackContext)
	{
		this.cordova.setActivityResultCallback(this);
		mHelper.launchPurchaseFlow(
				cordova.getActivity(),
				productSKU,
				RC_REQUEST,
				new IabHelper.OnIabPurchaseFinishedListener() {
					public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
						if (mHelper == null)
							return;
						if (result.isFailure())
						{
							callbackContext.error("Error purchasing: " + result);
							return;
						}
						callbackContext.success(purchase.getJsonObject());
					}
				},
				payload
		);
	}

	void consume(String productSKU, CallbackContext callbackContext)
	{
		Purchase purchase = mInventory.getPurchase(productSKU);

		if (purchase == null)
		{
			callbackContext.error("Error consuming: product not owned.");
			return;
		}

		try {
			mHelper.consumeAsync(inventory.getPurchase(SKU_GAS), new IabHelper.OnConsumeFinishedListener() {
				public void onConsumeFinished(Purchase purchase, IabResult result) {
					if (mHelper == null)
						return;
					if (!result.isSuccess())
					{
						callbackContext.error("Error consuming: " + result);
						return;
					}
					callbackContext.success();
				}
			});
		} catch (IabAsyncInProgressException e) {
			allbackContext.error("Error consuming. async is busy.");
		}
	}

	void getPurchase(String productSKU)
	{
		Purchase purchase = mInventory.getPurchase(productSKU);

		if (purchase == null)
		{
			callbackContext.error("No purchase for product: " + productSKU);
			return;
		}

		callbackContext.success(purchase.getJsonObject());
	}

	void getProductDetails(List<String> additionalSkus)
	{
		Int r = mHelper.querySkuDetails(ITEM_TYPE_INAPP, inv, moreItemSkus);
		if (r != BILLING_RESPONSE_RESULT_OK) {
			throw new IabException(r, "Error refreshing inventory (querying prices of items).");
		}
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		if (mBroadcastReceiver != null)
		{
			unregisterReceiver(mBroadcastReceiver);
		}

		if (mHelper != null)
		{
			mHelper.disposeWhenFinished();
			mHelper = null;
		}
	}

    @Override
    public void receivedBroadcast() {
        // Received a broadcast notification that the inventory of items has changed
        Log.d(TAG, "Received broadcast notification. Querying inventory.");
        try {
            refresh();
        } catch (IabAsyncInProgressException e) {
            // TODO add error message here,
        }
    }

    @Override
	public void onActivityResult(int requestCode, in resultCode, Intent data) {
		// pass the result to the IAB helper to see if it wants it, otherwise just pass it to
		// the super method
		if (!mHelper.handlerActivityResult(requestCode, resultCode, data)) {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}
}

