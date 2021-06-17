/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-present eyeo GmbH
 *
 * Adblock Plus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * Adblock Plus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Adblock Plus.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.adblockplus.libadblockplus.android.webviewapp;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.PermissionChecker;
import androidx.fragment.app.Fragment;

import org.adblockplus.libadblockplus.android.settings.AdblockHelper;
import org.adblockplus.libadblockplus.android.webview.AdblockWebView;
import org.adblockplus.libadblockplus.android.webview.WebViewCounters;

import mozilla.components.support.utils.DownloadUtils;
import timber.log.Timber;

public class TabFragment extends Fragment
{
  // sitekeys can be used for allowlisting [Optional and have small negative impact on performance]
  public static final boolean SITEKEYS_ALLOWLISTING = true;

  private static final String TITLE = "title";
  private static final String CUSTOM_INTERCEPT = "custom_intercept";
  private static final String NAVIGATE_TO_URL = "navigate_to_url";
  private static final String SAVED_TAB_BUNDLE = "saved_tab_bundle";
  private static final String SAVED_WEBVIEW_STATE = "saved_webview_state";
  private static final String SAVED_URL_STATE = "saved_URL_state";

  private String title;
  private boolean useCustomIntercept;
  private String navigateTo;

  private ProgressBar progress;
  private EditText url;
  private Button ok;
  private Button back;
  private Button forward;
  private TextView blockedCounter;
  private TextView allowlistedCounter;
  private AdblockWebView webView;

  private String downloadUrl;
  private String downloadUserAgent;
  private String downloadContentDisposition;
  private String downloadMimetype;

  /**
   * Factory method
   *
   * @param title              tab title
   * @param useCustomIntercept (used for QA) will add #TabInterceptingWebViewClient
   *                           instead #TabWebViewClient to the WebView
   *                           #TabInterceptingWebViewClient uses custom shouldInterceptRequest
   * @return fragment instance
   */
  public static TabFragment newInstance(final String title,
                                        final String navigateToUrl,
                                        final boolean useCustomIntercept)
  {
    final Bundle arguments = new Bundle();
    arguments.putString(TITLE, title);
    arguments.putString(NAVIGATE_TO_URL, navigateToUrl);
    arguments.putBoolean(CUSTOM_INTERCEPT, useCustomIntercept);
    final TabFragment newFragment = new TabFragment();
    newFragment.setArguments(arguments);
    return newFragment;
  }

  @Override
  public void onCreate(final Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    assert getArguments() != null;
    this.title = getArguments().getString(TITLE);
    this.useCustomIntercept = getArguments().getBoolean(CUSTOM_INTERCEPT, false);
    this.navigateTo = getArguments().getString(NAVIGATE_TO_URL, null);
  }

  @Override
  public View onCreateView(final LayoutInflater inflater,
                           final ViewGroup container,
                           final Bundle savedInstanceState)
  {
    final View view = inflater.inflate(R.layout.fragment_tab, container, false);
    bindControls(view);
    initControls();
    ((MainActivity) getActivity()).checkResume(this);
    return view;
  }

  @Override
  public void onDestroyView()
  {
    webView.dispose(null); // Release it when webView is no longer needed
    super.onDestroyView();
  }

  public String getTitle()
  {
    return title;
  }

  private void bindControls(final View rootView)
  {
    url = rootView.findViewById(R.id.fragment_tab_url);
    ok = rootView.findViewById(R.id.fragment_tab_ok);
    back = rootView.findViewById(R.id.fragment_tab_back);
    forward = rootView.findViewById(R.id.fragment_tab_forward);
    progress = rootView.findViewById(R.id.fragment_tab_progress);
    blockedCounter = rootView.findViewById(R.id.fragment_tab_blocked_counter);
    allowlistedCounter = rootView.findViewById(R.id.fragment_tab_wl_counter);
    webView = rootView.findViewById(R.id.fragment_tab_webview);
  }

  private void setProgressVisible(final boolean visible)
  {
    progress.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
  }

  private class TabWebViewClient extends WebViewClient
  {
    @Override
    public void onPageStarted(final WebView view, final String url, final Bitmap favicon)
    {
      Timber.d("onPageStarted called for url %s", url);
      setProgressVisible(true);

      // show updated URL (because of possible redirection)
      TabFragment.this.url.setText(url);
    }

    @Override
    public void onPageFinished(final WebView view, final String url)
    {
      Timber.d("onPageFinished called for url %s", url);
      setProgressVisible(false);
      updateButtons();
    }

    @Override
    public void onReceivedError(final WebView view, final int errorCode,
                                final String description, final String failingUrl)
    {
      updateButtons();
    }
  }

  /**
   * This one is used for QAing if custom `shouldInterceptRequest`
   * It adds `X-Modified-Intercept` request header to a web request
   * Might be checked
   */
  private class TabInterceptingWebViewClient extends TabWebViewClient
  {
    @Nullable
    @Override
    public WebResourceResponse shouldInterceptRequest(final WebView view, final WebResourceRequest request)
    {
      request.getRequestHeaders().put("X-Modified-Intercept", "true");
      return super.shouldInterceptRequest(view, request);
    }
  }

  public boolean saveTabState(final Context context, final int id)
  {
    if (url.getText() == null || url.getText().toString().isEmpty())
    {
      Timber.d("saveTabState() skips empty tab");
      return false;
    }
    Bundle outState = StorageUtils.readBundle(context, SAVED_TAB_BUNDLE + id);
    if (outState == null)
    {
      Timber.d("saveTabState() creates new outState bundle!");
      outState = new Bundle(ClassLoader.getSystemClassLoader());
    }
    final Bundle currentWebViewState = new Bundle(ClassLoader.getSystemClassLoader());
    if (webView.saveState(currentWebViewState) == null)
    {
      Timber.d("saveTabState() failed to obtain WebView state to save!");
    }
    Timber.d("saveTabState() saves tab %d", id);
    outState.putBundle(SAVED_WEBVIEW_STATE, currentWebViewState);
    outState.putString(SAVED_URL_STATE, webView.getUrl());
    StorageUtils.writeBundle(context, outState, SAVED_TAB_BUNDLE + id);
    Timber.d("saveTabState() saved tab url %s", webView.getUrl());
    return true;
  }

  public static void deleteTabState(final Context context, final int id)
  {
    Timber.d("deleteTabState() deletes tab %d", id);
    StorageUtils.deleteBundle(context, SAVED_TAB_BUNDLE + id);
  }

  public void restoreTabState(final Context context, final int id)
  {
    final Bundle savedState = StorageUtils.readBundle(context, SAVED_TAB_BUNDLE + id);
    if (savedState == null)
    {
      Timber.d("restoreTabState() savedState == null, exiting");
      return;
    }
    if (savedState.getBundle(SAVED_WEBVIEW_STATE) != null && savedState.getString(SAVED_URL_STATE) != null)
    {
      Timber.d("restoreTabState() restores tab %d", id);
      webView.restoreState(savedState.getBundle(SAVED_WEBVIEW_STATE));
      final String urlStr = savedState.getString(SAVED_URL_STATE);
      url.setText(urlStr);
      Timber.d("restoreTabState() restored tab url %s", urlStr);
    }
    else
    {
      Timber.d("restoreTabState() fails to restore tab %d", id);
    }
  }

  private void updateButtons()
  {
    back.setEnabled(webView.canGoBack());
    forward.setEnabled(webView.canGoForward());
  }

  private final WebChromeClient webChromeClient = new WebChromeClient()
  {
    @Override
    public void onProgressChanged(final WebView view, final int newProgress)
    {
      Timber.d("onProgressChanged to %d%% for url: %s", newProgress, view.getUrl());
      progress.setProgress(newProgress);
    }

    @Override
    public void onPermissionRequest(final PermissionRequest request)
    {
      // A very rough example to enable playing DRM video content
      final String[] perms = {PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID};
      request.grant(perms);
    }
  };

  void navigate(final String url)
  {
    if (webView != null && url != null)
    {
      webView.loadUrl(url);
    }
  }

  private void initControls()
  {
    url.setOnKeyListener((v, keyCode, event) ->
    {
      if (event.getAction() == KeyEvent.ACTION_DOWN &&
        keyCode == KeyEvent.KEYCODE_ENTER)
      {
        loadUrl();
      }
      return false;
    });

    ok.setOnClickListener(view -> loadUrl());

    back.setOnClickListener(v -> loadPrev());

    forward.setOnClickListener(v -> TabFragment.this.loadForward());

    initAdblockWebView();

    final AdblockWebView.EventsListener eventsListener = WebViewCounters.bindAdblockWebView(
      new WebViewCounters.EventsListener()
      {
        @Override
        public void onBlockedChanged(final int newValue)
        {
          blockedCounter.setText(String.valueOf(newValue));
        }

        @Override
        public void onAllowlistedChanged(final int newValue)
        {
          allowlistedCounter.setText(String.valueOf(newValue));
        }
      });
    webView.setEventsListener(eventsListener);

    setProgressVisible(false);
    updateButtons();

    // to show that external WebViewClient is still working
    webView.setWebViewClient(useCustomIntercept ?
      new TabInterceptingWebViewClient() : new TabWebViewClient());

    // to show that external WebChromeClient is still working
    webView.setWebChromeClient(webChromeClient);

    // to enable local storage for HTML5
    webView.getSettings().setDomStorageEnabled(true);

    final ActivityResultLauncher<String> writeStoragePermissionResult = registerForActivityResult(
      new ActivityResultContracts.RequestPermission(),
      isPermissionGranted ->
      {
        if (isPermissionGranted)
        {
          showDownloadDialog();
        }
      });

    webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) ->
    {
      downloadUrl = url;
      downloadUserAgent = userAgent;
      downloadContentDisposition = contentDisposition;
      downloadMimetype = mimetype;

      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
      {
        showDownloadDialog();
      }
      else
      {
        if (PermissionChecker
          .checkSelfPermission(TabFragment.this.getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
          PermissionChecker.PERMISSION_GRANTED)
        {
          showDownloadDialog();
        }
        else
        {
          writeStoragePermissionResult.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
      }
    });

    // if using custom intercept
    // we would already navigate to the
    // page that will show the headers in order to
    // check if ""X-Modified-Intercept" is there
    if (useCustomIntercept)
    {
      webView.loadUrl("https://request.urih.com/");
    }
  }

  private void showDownloadDialog()
  {

    final String fileName = DownloadUtils
      .guessFileName(downloadContentDisposition, null, downloadUrl, downloadMimetype);

    final AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
      .setTitle(getResources().getString(R.string.download_dialog_title))
      .setMessage(String.format(getResources().getString(R.string.download_dialog_text), fileName))
      .setPositiveButton(getResources().getString(R.string.download_dialog_yes), (dialog, which) ->
      {
        final DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl))
          .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(downloadUrl))
          .addRequestHeader("User-Agent", downloadUserAgent)
          .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
          .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.allowScanningByMediaScanner();
        final DownloadManager dm =
          (DownloadManager) TabFragment.this.getContext().getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm != null)
        {
          dm.enqueue(request);
          Toast.makeText(TabFragment.this.getContext(),
            getResources().getString(R.string.download_toast_message),
            Toast.LENGTH_LONG).show();
        }
      })
      .setNegativeButton(getResources().getString(R.string.download_dialog_no), (dialog, which) -> dialog.cancel());
    builder.create().show();
  }

  public void setJsInIframesEnabled(final boolean enabled)
  {
    webView.enableJsInIframes(enabled);
  }

  private void initAdblockWebView()
  {
    // use shared filters data (not to increase memory consumption)
    webView.setProvider(AdblockHelper.get().getProvider());

    if (SITEKEYS_ALLOWLISTING)
    {
      webView.setSiteKeysConfiguration(AdblockHelper.get().getSiteKeysConfiguration());
      webView.enableJsInIframes(((MainActivity) getActivity()).elemHideInInframesEnabled());
    }

    this.navigate(navigateTo);
  }

  private void loadPrev()
  {
    Utils.hideSoftwareKeyboard(url);
    if (webView.canGoBack())
    {
      webView.goBack();
    }
  }

  private void loadForward()
  {
    Utils.hideSoftwareKeyboard(url);
    if (webView.canGoForward())
    {
      webView.goForward();
    }
  }

  private String prepareUrl(String url)
  {
    if (!url.startsWith("http"))
    {
      url = "http://" + url;
    }

    // make sure url is valid URL
    return url;
  }

  private void loadUrl()
  {
    Utils.hideSoftwareKeyboard(url);
    webView.loadUrl(prepareUrl(url.getText().toString()));
  }
}
