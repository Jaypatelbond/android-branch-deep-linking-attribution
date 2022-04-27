package io.branch.referral;

import android.app.Application;
import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.net.URLEncoder;
import java.util.Collection;

/**
 * * <p>
 * The server request for creating a synchronous or asynchronous short url. Handles request creation and execution.
 * </p>
 */
class ServerRequestCreateUrl extends ServerRequest {

    private BranchLinkData linkPost_;
    private boolean isAsync_ = true;
    private Branch.BranchLinkCreateListener callback_;
    /* Default long link base url*/
    private static final String DEF_BASE_URL = "https://bnc.lt/a/";
    private boolean defaultToLongUrl_ = true;

    /**
     * <p>Configures and requests a short URL to be generated by the Branch servers, via a synchronous
     * call; with a duration specified within which an app session should be matched to the link.</p>
     *
     * @param context  Current {@link Application} context
     * @param alias    Link 'alias' can be used to label the endpoint on the link.     *
     *                 <p>
     *                 For example:
     *                 http://bnc.lt/AUSTIN28.
     *                 Should not exceed 128 characters
     *                 </p>
     * @param type     An {@link int} that can be used for scenarios where you want the link to
     *                 only deep link the first time.
     * @param duration A {@link Integer} value specifying the time that Branch allows a click to
     *                 remain outstanding and be eligible to be matched with a new app session
     * @param tags     An iterable {@link Collection} of {@link String} tags associated with a deep
     *                 link.
     * @param channel  A {@link String} denoting the channel that the link belongs to. Should not
     *                 exceed 128 characters.
     * @param feature  A {@link String} value identifying the feature that the link makes use of.
     *                 Should not exceed 128 characters.
     * @param stage    A {@link String} value identifying the stage in an application or user flow
     *                 process. Should not exceed 128 characters.
     * @param campaign A {@link String} denoting the campaign that the link belongs to. Should not
     *                 exceed 128 characters.
     * @param params   A {@link JSONObject} value containing the deep linked params associated with
     *                 the link that will be passed into a new app session when clicked
     * @param callback A {@link Branch.BranchLinkCreateListener} callback instance that will trigger
     *                 actions defined therein upon receipt of a response to a create link request.
     * @param async    {@link Boolean} value specifying whether to get the url asynchronously or not.
     */
    public ServerRequestCreateUrl(Context context, final String alias, final int type, final int duration,
                                  final Collection<String> tags, final String channel, final String feature,
                                  final String stage, final String campaign, final JSONObject params,
                                  Branch.BranchLinkCreateListener callback, boolean async, boolean defaultToLongUrl) {
        super(context, Defines.RequestPath.GetURL);

        callback_ = callback;
        isAsync_ = async;
        defaultToLongUrl_ = defaultToLongUrl;

        linkPost_ = new BranchLinkData();
        try {
            linkPost_.put(Defines.Jsonkey.IdentityID.getKey(), prefHelper_.getIdentityID());
            linkPost_.put(Defines.Jsonkey.DeviceFingerprintID.getKey(), prefHelper_.getDeviceFingerPrintID());
            linkPost_.put(Defines.Jsonkey.SessionID.getKey(), prefHelper_.getSessionID());
            if (!prefHelper_.getLinkClickID().equals(PrefHelper.NO_STRING_VALUE)) {
                linkPost_.put(Defines.Jsonkey.LinkClickID.getKey(), prefHelper_.getLinkClickID());
            }

            linkPost_.putType(type);
            linkPost_.putDuration(duration);
            linkPost_.putTags(tags);
            linkPost_.putAlias(alias);
            linkPost_.putChannel(channel);
            linkPost_.putFeature(feature);
            linkPost_.putStage(stage);
            linkPost_.putCampaign(campaign);
            linkPost_.putParams(params);

            setPost(linkPost_);

        } catch (JSONException ex) {
            ex.printStackTrace();
            constructError_ = true;
        }

    }

    public ServerRequestCreateUrl(Defines.RequestPath requestPath, JSONObject post, Context context) {
        super(requestPath, post, context);
    }

    public BranchLinkData getLinkPost() {
        return linkPost_;
    }

    boolean isDefaultToLongUrl() {
        return defaultToLongUrl_;
    }

    @Override
    public boolean handleErrors(Context context) {
        if (!super.doesAppHasInternetPermission(context)) {
            if (callback_ != null) {
                callback_.onLinkCreate(null, new BranchError("Trouble creating a URL.", BranchError.ERR_NO_INTERNET_PERMISSION));
            }
            return true;
        }
        return false;
    }

    @Override
    public void onRequestSucceeded(ServerResponse resp, Branch branch) {
        try {
            final String url = resp.getObject().getString("url");
            if (callback_ != null) {
                callback_.onLinkCreate(url, null);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Calls the callback with the URL. This should be called on finding an existing url
     * up on trying to create a URL asynchronously
     *
     * @param url existing url with for the given data
     */
    public void onUrlAvailable(String url) {
        if (callback_ != null) {
            callback_.onLinkCreate(url, null);
        }
    }

    @Override
    public void handleFailure(int statusCode, String causeMsg) {
        if (callback_ != null) {
            String failedUrl = null;
            if (defaultToLongUrl_) {
                failedUrl = getLongUrl();
            }
            callback_.onLinkCreate(failedUrl, new BranchError("Trouble creating a URL. " + causeMsg, statusCode));
        }
    }

    public String getLongUrl() {
        String longUrl;
        if (!prefHelper_.getUserURL().equals(PrefHelper.NO_STRING_VALUE)) {
            longUrl = generateLongUrlWithParams(prefHelper_.getUserURL());
        } else {
            longUrl = generateLongUrlWithParams(DEF_BASE_URL + prefHelper_.getBranchKey());
        }
        return longUrl;
    }

    public void handleDuplicateURLError() {
        if (callback_ != null) {
            callback_.onLinkCreate(null, new BranchError("Trouble creating a URL.", BranchError.ERR_BRANCH_DUPLICATE_URL));
        }
    }

    @Override
    public boolean isGetRequest() {
        return false;
    }

    @Override
    public void
    clearCallbacks() {
        callback_ = null;
    }

    public boolean isAsync() {
        return isAsync_;
    }

    /**
     * Generates a long url with the given deep link parameters and link properties
     *
     * @return A {@link String} url with given deep link parameters
     */
    private String generateLongUrlWithParams(String baseUrl) {
        String longUrl = baseUrl;
        try {
            if (Branch.getInstance().isTrackingDisabled() && !longUrl.contains(DEF_BASE_URL)) {
                // By def the base url contains identity id as query param. This should be removed when tracking is disabled.
                longUrl = longUrl.replace(new URL(longUrl).getQuery(), "");
            }
            longUrl += longUrl.contains("?") ? "" : "?";
            longUrl += longUrl.endsWith("?") ? "" : "&";

            Collection<String> tags = linkPost_.getTags();
            if (tags != null) {
                for (String tag : tags) {
                    if (tag != null && tag.length() > 0)
                        longUrl = longUrl + Defines.LinkParam.Tags + "=" + URLEncoder.encode(tag, "UTF8") + "&";
                }
            }
            String alias = linkPost_.getAlias();
            if (alias != null && alias.length() > 0) {
                longUrl = longUrl + Defines.LinkParam.Alias + "=" + URLEncoder.encode(alias, "UTF8") + "&";
            }

            String channel = linkPost_.getChannel();
            if (channel != null && channel.length() > 0) {
                longUrl = longUrl + Defines.LinkParam.Channel + "=" + URLEncoder.encode(channel, "UTF8") + "&";
            }

            String feature = linkPost_.getFeature();
            if (feature != null && feature.length() > 0) {
                longUrl = longUrl + Defines.LinkParam.Feature + "=" + URLEncoder.encode(feature, "UTF8") + "&";
            }

            String stage = linkPost_.getStage();
            if (stage != null && stage.length() > 0) {
                longUrl = longUrl + Defines.LinkParam.Stage + "=" + URLEncoder.encode(stage, "UTF8") + "&";
            }

            String campaign = linkPost_.getCampaign();
            if (campaign != null && campaign.length() > 0) {
                longUrl = longUrl + Defines.LinkParam.Campaign + "=" + URLEncoder.encode(campaign, "UTF8") + "&";
            }

            long type = linkPost_.getType();
            longUrl = longUrl + Defines.LinkParam.Type + "=" + type + "&";

            long duration = linkPost_.getDuration();
            longUrl = longUrl + Defines.LinkParam.Duration + "=" + duration;

            String params = linkPost_.getParams().toString();
            if (params != null && params.length() > 0) {
                byte[] data = params.getBytes();
                String base64Data = Base64.encodeToString(data, android.util.Base64.NO_WRAP);
                String urlEncodedBase64Data = URLEncoder.encode(base64Data, "UTF8");
                longUrl = longUrl + "&source=android&data=" + urlEncodedBase64Data;
            }
        } catch (Exception ignore) {
            callback_.onLinkCreate(null, new BranchError("Trouble creating a URL.", BranchError.ERR_BRANCH_INVALID_REQUEST));
        }

        return longUrl;
    }

    @Override
    boolean isPersistable() {
        return false; // No need to retrieve create url request from previous session
    }

    @Override
    protected boolean prepareExecuteWithoutTracking() {
        // SDK-271 -- Allow creation of short links when tracking is disabled.
        return true;
    }

}
