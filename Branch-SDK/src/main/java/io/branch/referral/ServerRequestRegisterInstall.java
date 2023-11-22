package io.branch.referral;

import android.app.Application;
import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * * <p>
 * The server request for registering an app install to Branch API. Handles request creation and execution.
 * </p>
 */
class ServerRequestRegisterInstall extends ServerRequestInitSession {
    
    /**
     * <p>Create an instance of {@link ServerRequestRegisterInstall} to notify Branch API on a new install.</p>
     *
     * @param context     Current {@link Application} context
     * @param callback    A {@link Branch.BranchReferralInitListener} callback instance that will return
     *                    the data associated with new install registration.
     */
    ServerRequestRegisterInstall(Context context, Branch.BranchReferralInitListener callback, boolean isAutoInitialization) {
        super(context, Defines.RequestPath.RegisterInstall, isAutoInitialization);
        callback_ = callback;
        try {
            setPost(new JSONObject());
        } catch (JSONException ex) {
            ex.printStackTrace();
            constructError_ = true;
        }
    }
    
    ServerRequestRegisterInstall(Defines.RequestPath requestPath, JSONObject post, Context context, boolean isAutoInitialization) {
        super(requestPath, post, context, isAutoInitialization);
    }

    @Override
    public void onPreExecute() {
        super.onPreExecute();
        long clickedReferrerTS = prefHelper_.getLong(PrefHelper.KEY_REFERRER_CLICK_TS);
        long installBeginTS = prefHelper_.getLong(PrefHelper.KEY_INSTALL_BEGIN_TS);
        try {
            if (clickedReferrerTS > 0) {
                getPost().put(Defines.Jsonkey.ClickedReferrerTimeStamp.getKey(), clickedReferrerTS);
            }
            if (installBeginTS > 0) {
                getPost().put(Defines.Jsonkey.InstallBeginTimeStamp.getKey(), installBeginTS);
            }
            if (!AppStoreReferrer.getInstallationID().equals(PrefHelper.NO_STRING_VALUE)) {
                getPost().put(Defines.Jsonkey.LinkClickID.getKey(), AppStoreReferrer.getInstallationID());
            }
        } catch (JSONException e) {
            BranchLogger.d(e.getMessage());
        }
    }
    
    @Override
    public void onRequestSucceeded(ServerResponse resp, Branch branch) {
        super.onRequestSucceeded(resp, branch);
        try {
            prefHelper_.setUserURL(resp.getObject().getString(Defines.Jsonkey.Link.getKey()));
            
            if (resp.getObject().has(Defines.Jsonkey.Data.getKey())) {
                JSONObject dataObj = new JSONObject(resp.getObject().getString(Defines.Jsonkey.Data.getKey()));
                // If Clicked on a branch link
                if (dataObj.has(Defines.Jsonkey.Clicked_Branch_Link.getKey())
                        && dataObj.getBoolean(Defines.Jsonkey.Clicked_Branch_Link.getKey())) {
                    
                    // Check if there is any install params. Install param will be empty on until click a branch link
                    // or When a user logout
                    if (prefHelper_.getInstallParams().equals(PrefHelper.NO_STRING_VALUE)) {
                        String params = resp.getObject().getString(Defines.Jsonkey.Data.getKey());
                        prefHelper_.setInstallParams(params);
                    }
                }
            }
            
            if (resp.getObject().has(Defines.Jsonkey.LinkClickID.getKey())) {
                prefHelper_.setLinkClickID(resp.getObject().getString(Defines.Jsonkey.LinkClickID.getKey()));
            } else {
                prefHelper_.setLinkClickID(PrefHelper.NO_STRING_VALUE);
            }
            
            if (resp.getObject().has(Defines.Jsonkey.Data.getKey())) {
                String params = resp.getObject().getString(Defines.Jsonkey.Data.getKey());
                prefHelper_.setSessionParams(params);
            } else {
                prefHelper_.setSessionParams(PrefHelper.NO_STRING_VALUE);
            }
            if (callback_ != null) {
                callback_.onInitFinished(branch.getLatestReferringParams(), null);
            }
            
            prefHelper_.setAppVersion(DeviceInfo.getInstance().getAppVersion());
            
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        onInitSessionCompleted(resp, branch);
    }
    
    @Override
    public void handleFailure(int statusCode, String causeMsg) {
        if (callback_ != null) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("error_message", "Trouble reaching server. Please try again in a few minutes");
            } catch (JSONException ex) {
                ex.printStackTrace();
            }
            callback_.onInitFinished(obj, new BranchError("Trouble initializing Branch. " + causeMsg, statusCode));
        }
    }
    
    @Override
    public boolean handleErrors(Context context) {
        if (!super.doesAppHasInternetPermission(context)) {
            if (callback_ != null) {
                callback_.onInitFinished(null, new BranchError("Trouble initializing Branch.", BranchError.ERR_NO_INTERNET_PERMISSION));
            }
            return true;
        }
        return false;
    }
    
    @Override
    public boolean isGetRequest() {
        return false;
    }
    
    @Override
    public void clearCallbacks() {
        BranchLogger.v(this + " clearCallbacks");
        callback_ = null;
    }
    
    @Override
    public String getRequestActionName() {
        return ACTION_INSTALL;
    }

    @Override
    public boolean shouldRetryOnFail() {
        return true;   //Install request needs to retry on failure.
    }
}
