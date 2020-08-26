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

package org.adblockplus.libadblockplus.android.webview;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Message;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebStorage;
import android.webkit.WebView;

import java.util.concurrent.atomic.AtomicReference;

/**
 * This is a proxy class of {@link WebChromeClient}
 * <p/>
 * The purpose of it is to reduce boilerplate code
 * when one needs to have external {@link WebChromeClient} along
 * with internal implementation
 * <p/>
 * Every methods just calls {@code client.[method name]}
 * falling back to super method if {@code extWebChromeClient} is null
 * <p/>
 * It is thread safe
 * @see AdblockWebView#AdblockWebChromeClient
 */
@SuppressWarnings({"unused", "JavadocReference", "ClassWithTooManyMethods"})
    // this is the API, it might have unused methods
class ProxyWebChromeClient extends WebChromeClient
{
  // We use Atomic here because  we are setting this client from app code => UI thread,
  // but it is then used (callback are called) from other threads like IO/Network.
  private final AtomicReference<WebChromeClient> extWebChromeClient;

  ProxyWebChromeClient(final WebChromeClient extWebChromeClient)
  {
    this.extWebChromeClient = new AtomicReference<>(extWebChromeClient);
  }

  public WebChromeClient getExtWebChromeClient()
  {
    return extWebChromeClient.get();
  }

  void setExtWebChromeClient(final WebChromeClient client)
  {
    this.extWebChromeClient.set(client);
  }

  @Override
  public void onPermissionRequest(final PermissionRequest request)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      client.onPermissionRequest(request);
    }
    else
    {
      super.onPermissionRequest(request);
    }
  }

  @Override
  public void onPermissionRequestCanceled(final PermissionRequest request)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      client.onPermissionRequestCanceled(request);
    }
    else
    {
      super.onPermissionRequestCanceled(request);
    }
  }

  @Override
  public void onReceivedTitle(final WebView view, final String title)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      client.onReceivedTitle(view, title);
    }
    else
    {
      super.onReceivedTitle(view, title);
    }
  }

  @Override
  public void onReceivedIcon(final WebView view, final Bitmap icon)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      client.onReceivedIcon(view, icon);
    }
    else
    {
      super.onReceivedIcon(view, icon);
    }
  }

  @Override
  public void onReceivedTouchIconUrl(final WebView view, final String url, final boolean precomposed)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      client.onReceivedTouchIconUrl(view, url, precomposed);
    }
    else
    {
      super.onReceivedTouchIconUrl(view, url, precomposed);
    }
  }

  @Override
  public void onShowCustomView(final View view, final CustomViewCallback callback)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      client.onShowCustomView(view, callback);
    }
    else
    {
      super.onShowCustomView(view, callback);
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public void onShowCustomView(final View view, final int requestedOrientation,
                               final CustomViewCallback callback)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      client.onShowCustomView(view, requestedOrientation, callback);
    }
    else
    {
      super.onShowCustomView(view, requestedOrientation, callback);
    }
  }

  @Override
  public void onHideCustomView()
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      client.onHideCustomView();
    }
    else
    {
      super.onHideCustomView();
    }
  }

  @Override
  public boolean onCreateWindow(final WebView view, final boolean isDialog,
                                final boolean isUserGesture,
                                final Message resultMsg)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      return client.onCreateWindow(view, isDialog, isUserGesture, resultMsg);
    }
    else
    {
      return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg);
    }
  }

  @Override
  public void onRequestFocus(final WebView view)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      client.onRequestFocus(view);
    }
    else
    {
      super.onRequestFocus(view);
    }
  }

  @Override
  public void onCloseWindow(final WebView window)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      client.onCloseWindow(window);
    }
    else
    {
      super.onCloseWindow(window);
    }
  }

  @Override
  public boolean onJsAlert(final WebView view, final String url, final String message,
                           final JsResult result)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      return client.onJsAlert(view, url, message, result);
    }
    else
    {
      return super.onJsAlert(view, url, message, result);
    }
  }

  @Override
  public boolean onJsConfirm(final WebView view, final String url, final String message,
                             final JsResult result)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      return client.onJsConfirm(view, url, message, result);
    }
    else
    {
      return super.onJsConfirm(view, url, message, result);
    }
  }

  @Override
  public boolean onJsPrompt(final WebView view, final String url, final String message,
                            final String defaultValue,
                            final JsPromptResult result)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      return client.onJsPrompt(view, url, message, defaultValue, result);
    }
    else
    {
      return super.onJsPrompt(view, url, message, defaultValue, result);
    }
  }

  @Override
  public boolean onJsBeforeUnload(final WebView view, final String url, final String message,
                                  final JsResult result)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      return client.onJsBeforeUnload(view, url, message, result);
    }
    else
    {
      return super.onJsBeforeUnload(view, url, message, result);
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public void onExceededDatabaseQuota(final String url,
                                      final String databaseIdentifier,
                                      final long quota,
                                      final long estimatedDatabaseSize,
                                      final long totalQuota,
                                      final WebStorage.QuotaUpdater quotaUpdater)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      client.onExceededDatabaseQuota(url, databaseIdentifier, quota,
          estimatedDatabaseSize, totalQuota, quotaUpdater);
    }
    else
    {
      super.onExceededDatabaseQuota(url, databaseIdentifier, quota,
          estimatedDatabaseSize, totalQuota, quotaUpdater);
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public void onReachedMaxAppCacheSize(final long requiredStorage, final long quota,
                                       final WebStorage.QuotaUpdater quotaUpdater)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      client.onReachedMaxAppCacheSize(requiredStorage, quota, quotaUpdater);
    }
    else
    {
      super.onReachedMaxAppCacheSize(requiredStorage, quota, quotaUpdater);
    }
  }

  @Override
  public void onGeolocationPermissionsShowPrompt(final String origin,
                                                 final GeolocationPermissions.Callback callback)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      client.onGeolocationPermissionsShowPrompt(origin, callback);
    }
    else
    {
      super.onGeolocationPermissionsShowPrompt(origin, callback);
    }
  }

  @Override
  public void onGeolocationPermissionsHidePrompt()
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      client.onGeolocationPermissionsHidePrompt();
    }
    else
    {
      super.onGeolocationPermissionsHidePrompt();
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public boolean onJsTimeout()
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      return client.onJsTimeout();
    }
    else
    {
      return super.onJsTimeout();
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public void onConsoleMessage(final String message, final int lineNumber, final String sourceID)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      client.onConsoleMessage(message, lineNumber, sourceID);
    }
    else
    {
      super.onConsoleMessage(message, lineNumber, sourceID);
    }
  }

  @Override
  public boolean onConsoleMessage(final ConsoleMessage consoleMessage)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      return client.onConsoleMessage(consoleMessage);
    }
    else
    {
      return super.onConsoleMessage(consoleMessage);
    }
  }

  @Override
  public Bitmap getDefaultVideoPoster()
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      return client.getDefaultVideoPoster();
    }
    else
    {
      return super.getDefaultVideoPoster();
    }
  }

  @Override
  public View getVideoLoadingProgressView()
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      return client.getVideoLoadingProgressView();
    }
    else
    {
      return super.getVideoLoadingProgressView();
    }
  }

  @Override
  public void getVisitedHistory(final ValueCallback<String[]> callback)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      client.getVisitedHistory(callback);
    }
    else
    {
      super.getVisitedHistory(callback);
    }
  }

  @Override
  public void onProgressChanged(final WebView view, final int newProgress)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      client.onProgressChanged(view, newProgress);
    }
    else
    {
      super.onProgressChanged(view, newProgress);
    }
  }

  @Override
  public boolean onShowFileChooser(final WebView webView,
                                   final ValueCallback<Uri[]> filePathCallback,
                                   final FileChooserParams fileChooserParams)
  {
    final WebChromeClient client = extWebChromeClient.get();
    if (client != null)
    {
      return client.onShowFileChooser(webView, filePathCallback, fileChooserParams);
    }
    else
    {
      return super.onShowFileChooser(webView, filePathCallback, fileChooserParams);
    }
  }
}
