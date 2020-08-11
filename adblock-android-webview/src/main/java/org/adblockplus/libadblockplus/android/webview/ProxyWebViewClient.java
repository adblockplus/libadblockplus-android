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

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.os.Message;
import android.view.KeyEvent;
import android.webkit.ClientCertRequest;
import android.webkit.HttpAuthHandler;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SafeBrowsingResponse;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.concurrent.atomic.AtomicReference;

/**
 * This is a proxy class of {@link WebViewClient}
 * <p/>
 * The purpose of it is to reduce boilerplate code
 * when one needs to have external {@link WebViewClient} along
 * with internal implementation
 * <p/>
 * Every methods just calls {@code client.[method name]}
 * falling back to super method if {@code extWebViewClient} is null
 * <p/>
 * It is thread safe
 *
 * @see AdblockWebView#AdblockWebViewClient
 */
@SuppressWarnings({"unused", "JavadocReference", "ClassWithTooManyMethods"})
  // this is the API, it might have unused methods
class ProxyWebViewClient extends WebViewClient
{
  // We use Atomic here because  we are setting this client from app code => UI thread,
  // but it is then used (callback are called) from other threads like IO/Network.
  private final AtomicReference<WebViewClient> extWebViewClient;

  ProxyWebViewClient(final WebViewClient extWebViewClient)
  {
    this.extWebViewClient = new AtomicReference<>(extWebViewClient);
  }

  @SuppressWarnings("WeakerAccess")
  public WebViewClient getExtWebViewClient()
  {
    return extWebViewClient.get();
  }

  void setExtWebViewClient(final WebViewClient client)
  {
    this.extWebViewClient.set(client);
  }

  @TargetApi(Build.VERSION_CODES.N)
  @Override
  public boolean shouldOverrideUrlLoading(final WebView view, final WebResourceRequest request)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      return client.shouldOverrideUrlLoading(view, request);
    }
    else
    {
      return super.shouldOverrideUrlLoading(view, request);
    }
  }

  @Override
  public boolean shouldOverrideUrlLoading(final WebView view, final String url)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      return client.shouldOverrideUrlLoading(view, url);
    }
    else
    {
      return super.shouldOverrideUrlLoading(view, url);
    }
  }

  @Override
  public void onPageStarted(final WebView view, final String url, final Bitmap favicon)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      client.onPageStarted(view, url, favicon);
    }
    else
    {
      super.onPageStarted(view, url, favicon);
    }
  }

  @Override
  public void onPageFinished(final WebView view, final String url)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      client.onPageFinished(view, url);
    }
    else
    {
      super.onPageFinished(view, url);
    }
  }

  @Override
  public void onLoadResource(final WebView view, final String url)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      client.onLoadResource(view, url);
    }
    else
    {
      super.onLoadResource(view, url);
    }
  }

  @TargetApi(Build.VERSION_CODES.M)
  @Override
  public void onPageCommitVisible(final WebView view, final String url)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      client.onPageCommitVisible(view, url);
    }
    else
    {
      super.onPageCommitVisible(view, url);
    }
  }

  @TargetApi(Build.VERSION_CODES.O)
  @Override
  public boolean onRenderProcessGone(final WebView view, final RenderProcessGoneDetail detail)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      return client.onRenderProcessGone(view, detail);
    }
    else
    {
      return super.onRenderProcessGone(view, detail);
    }
  }

  @TargetApi(Build.VERSION_CODES.O_MR1)
  @Override
  public void onSafeBrowsingHit(final WebView view, final WebResourceRequest request,
                                final int threatType, final SafeBrowsingResponse callback)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      client.onSafeBrowsingHit(view, request, threatType, callback);
    }
    else
    {
      super.onSafeBrowsingHit(view, request, threatType, callback);
    }
  }

  @Override
  public void onReceivedClientCertRequest(final WebView view, final ClientCertRequest request)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      client.onReceivedClientCertRequest(view, request);
    }
    else
    {
      super.onReceivedClientCertRequest(view, request);
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public void onTooManyRedirects(final WebView view, final Message cancelMsg,
                                 final Message continueMsg)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      client.onTooManyRedirects(view, cancelMsg, continueMsg);
    }
    else
    {
      super.onTooManyRedirects(view, cancelMsg, continueMsg);
    }
  }

  @Override
  public void onReceivedError(final WebView view, final int errorCode, final String description,
                              final String failingUrl)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      client.onReceivedError(view, errorCode, description, failingUrl);
    }
    else
    {
      super.onReceivedError(view, errorCode, description, failingUrl);
    }
  }

  @TargetApi(Build.VERSION_CODES.M)
  @Override
  public void onReceivedError(final WebView view, final WebResourceRequest request,
                              final WebResourceError error)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      client.onReceivedError(view, request, error);
    }
    else
    {
      super.onReceivedError(view, request, error);
    }
  }

  @Override
  public void onFormResubmission(final WebView view, final Message dontResend, final Message resend)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      client.onFormResubmission(view, dontResend, resend);
    }
    else
    {
      super.onFormResubmission(view, dontResend, resend);
    }
  }

  @Override
  public void doUpdateVisitedHistory(final WebView view, final String url, final boolean isReload)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      client.doUpdateVisitedHistory(view, url, isReload);
    }
    else
    {
      super.doUpdateVisitedHistory(view, url, isReload);
    }
  }

  @Override
  public void onReceivedSslError(final WebView view, final SslErrorHandler handler,
                                 final SslError error)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      client.onReceivedSslError(view, handler, error);
    }
    else
    {
      super.onReceivedSslError(view, handler, error);
    }
  }

  @Override
  public void onReceivedHttpAuthRequest(final WebView view, final HttpAuthHandler handler,
                                        final String host, final String realm)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      client.onReceivedHttpAuthRequest(view, handler, host, realm);
    }
    else
    {
      super.onReceivedHttpAuthRequest(view, handler, host, realm);
    }
  }

  @TargetApi(Build.VERSION_CODES.M)
  @Override
  public void onReceivedHttpError(final WebView view, final WebResourceRequest request,
                                  final WebResourceResponse errorResponse)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      client.onReceivedHttpError(view, request, errorResponse);
    }
    else
    {
      super.onReceivedHttpError(view, request, errorResponse);
    }
  }

  @Override
  public boolean shouldOverrideKeyEvent(final WebView view, final KeyEvent event)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      return client.shouldOverrideKeyEvent(view, event);
    }
    else
    {
      return super.shouldOverrideKeyEvent(view, event);
    }
  }

  @Override
  public void onUnhandledKeyEvent(final WebView view, final KeyEvent event)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      client.onUnhandledKeyEvent(view, event);
    }
    else
    {
      super.onUnhandledKeyEvent(view, event);
    }
  }

  @Override
  public void onScaleChanged(final WebView view, final float oldScale, final float newScale)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      client.onScaleChanged(view, oldScale, newScale);
    }
    else
    {
      super.onScaleChanged(view, oldScale, newScale);
    }
  }

  @Override
  public void onReceivedLoginRequest(final WebView view, final String realm, final String account,
                                     final String args)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      client.onReceivedLoginRequest(view, realm, account, args);
    }
    else
    {
      super.onReceivedLoginRequest(view, realm, account, args);
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  @Override
  public WebResourceResponse shouldInterceptRequest(final WebView view,
                                                    final WebResourceRequest request)
  {
    final WebViewClient client = extWebViewClient.get();
    if (client != null)
    {
      return client.shouldInterceptRequest(view, request);
    }
    else
    {
      return super.shouldInterceptRequest(view, request);
    }
  }
}
