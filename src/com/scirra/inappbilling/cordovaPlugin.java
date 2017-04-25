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
    IabHelper mHelper;
    // Provides purchase notification while this app is running
    IabBroadcastReceiver mBroadcastReceiver;

    Inventory mInventory;
	
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
			default:
				callbackContext.error("Invalid action");
		}
		
		return false;
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
                    // Have we been disposed of in the meantime? If so, quit.
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
                callbackContext.error("Failed querying inventory, async is busy.");
            else
                throw e;
        }
	}

	void refresh()
    {
        refresh(null);
    }

    @Override
    public void receivedBroadcast() {
        // Received a broadcast notification that the inventory of items has changed
        Log.d(TAG, "Received broadcast notification. Querying inventory.");
        try {
            refresh();
        } catch (IabAsyncInProgressException e) {
            complain("Error querying inventory. Another async operation in progress.");
        }
    }
	
	boolean verifyDeveloperPayload(Purchase p)
	{
		
		return true;
	}
}

