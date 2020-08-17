package org.adblockplus.libadblockplus.android.webview;

import android.os.Handler;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import org.adblockplus.libadblockplus.android.Utils;
import org.adblockplus.libadblockplus.sitekey.SiteKeyException;
import org.adblockplus.libadblockplus.sitekey.SiteKeyVerifier;

import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import timber.log.Timber;

/**
 * Extracts <i>Site Key</i> from `html.data-adblockkey` tag
 *
 * <ol>
 *   <li>Adds a JS interface that will listen to the received key</li>
 *   <li>Injects JS during {@link android.webkit.WebChromeClient#onProgressChanged(WebView, int)}.
 *   See "assets/inject.js"</li>
 *   <li>JS calls {@link JsCallbackInterface#onSiteKeyExtracted(String, String, String)}
 *   and verifies the sitekey with {@link SiteKeyVerifier}</li>
 * </ol>
 *
 * @see BaseSiteKeyExtractor
 */
class JsSiteKeyExtractor extends BaseSiteKeyExtractor
{
  private static final int HOLD_LOCK_TIMEOUT_S = 1;

  private CountDownLatch latch;
  private final AtomicBoolean isJavascriptInterfaceSet;
  private final Handler callerThreadHandler;
  private Runnable enableStateRunner;
  private Runnable disableStateRunner;

  JsSiteKeyExtractor(final AdblockWebView view)
  {
    super(view);
    callerThreadHandler = new Handler();
    view.addJavascriptInterface(new JsCallbackInterface(this), JsCallbackInterface.NAME);
    isJavascriptInterfaceSet = new AtomicBoolean(true);
  }

  @Override
  public void setEnabled(final boolean enabled)
  {
    super.setEnabled(enabled);
    final AdblockWebView webView = webViewWeakReference.get();
    if (webView == null)
    {
      return;
    }
    if (enabled)
    {
      // we'd like not to change states rapidly
      // in case if setEnabled will be called repeatedly
      if (!isJavascriptInterfaceSet.get())
      {
        // lazy init + doing it in place for better readability
        if (enableStateRunner == null)
        {
          enableStateRunner = new Runnable()
          {
            @Override
            public void run()
            {
              webView.addJavascriptInterface(
                  new JsCallbackInterface(JsSiteKeyExtractor.this),
                  JsCallbackInterface.NAME);
              isJavascriptInterfaceSet.set(true);
            }
          };
        }
        Timber.d("Enabling JsSiteKeyExtractor");
        callerThreadHandler.post(enableStateRunner);
      }
    }
    else
    {
      // lazy init + doing it in place for better readability
      if (disableStateRunner == null)
      {
        disableStateRunner = new Runnable()
        {
          @Override
          public void run()
          {
            webView.removeJavascriptInterface(JsCallbackInterface.NAME);
            isJavascriptInterfaceSet.set(false);
          }
        };
      }
      Timber.d("Disabling JsSiteKeyExtractor");
      callerThreadHandler.post(disableStateRunner);
      if (latch != null)
      {
        latch.countDown();
        latch = null;
      }
    }
  }

  @Override
  public WebResourceResponse obtainAndCheckSiteKey(final AdblockWebView webView,
                                                   final WebResourceRequest request)
  {
    // waitSitekeyCheck is used only blocking the network thread while
    // the key verification is ongoing
    if (isEnabled())
    {
      latch = new CountDownLatch(1);

      try
      {
        Timber.d("Holding request %s", request.getUrl().toString());
        latch.await(HOLD_LOCK_TIMEOUT_S, TimeUnit.SECONDS); // not expecting to wait more then a second
        Timber.d("Un-holding request %s", request.getUrl().toString());
      }
      catch (final InterruptedException error)
      {
        // not likely to happen
        Timber.e(error);
      }
    }
    return AdblockWebView.WebResponseResult.ALLOW_LOAD;
  }

  private void verifySiteKey(final String url, final String userAgent, final String value)
  {
    try
    {
      final SiteKeyVerifier verifier = getSiteKeysConfiguration().getSiteKeyVerifier();
      if (verifier == null)
      {
        throw new AssertionError("Verifier must be set before this is called");
      }
      else if (verifier.verify(Utils.getUrlWithoutAnchor(url), userAgent, value))
      {
        Timber.d("Url %s public key verified successfully", url);
      }
      else
      {
        Timber.e("Url %s public key is not verified", url);
      }
    }
    catch (final SiteKeyException e)
    {
      Timber.e(e, "Failed to verify sitekey header");
    }
  }

  public static class JsCallbackInterface
  {
    static final String NAME = "AbpCallback";
    private final WeakReference<JsSiteKeyExtractor> extractorRef;
    // keeping ref for the purpose of self-remove from WebView
    // in case of JsSiteKeyExtractor has been destroyed
    private final WeakReference<AdblockWebView> webViewRef;

    private JsCallbackInterface(final JsSiteKeyExtractor extractor)
    {
      this.extractorRef = new WeakReference<>(extractor);
      this.webViewRef = extractor.webViewWeakReference;
    }

    /**
     * If reference to JsSiteKeyExtractor is dead
     * remove self from Javascript interfaces
     *
     * @return JsSiteKeyExtractor if still exist
     */
    private JsSiteKeyExtractor getExtractorIfStillExist()
    {
      final JsSiteKeyExtractor extractor = extractorRef.get();
      if (extractor != null)
      {
        return extractor;
      }
      final AdblockWebView webView = webViewRef.get();
      if (webView != null)
      {
        webView.removeJavascriptInterface(NAME);
      }
      return null;
    }

    @JavascriptInterface
    public void onSiteKeyExtracted(final String key, final String url, final String userAgent)
    {
      Timber.d("Received sitekey for \nurl: %s", url);
      final JsSiteKeyExtractor extractor = getExtractorIfStillExist();
      if (extractor == null || !extractor.isEnabled())
      {
        return;
      }
      if (key != null && !key.isEmpty())
      {
        extractor.verifySiteKey(url, userAgent, key);
      }
      // it its null, then we are faster then
      // the call to `waitForSitekeyCheck` and its safe to ignore
      if (extractor.latch != null)
      {
        extractor.latch.countDown();
      }
    }

    @JavascriptInterface
    public void onSiteKeyDoesNotExist(final String url)
    {
      final JsSiteKeyExtractor extractor = getExtractorIfStillExist();
      if (extractor == null || !extractor.isEnabled())
      {
        return;
      }
      // this means that the DOM is ready
      // but the html site key does not exist
      // and no reason to wait more
      if (extractor.latch != null)
      {
        extractor.latch.countDown();
      }
      // collecting the url
      Timber.d("Key does not exist on url %s", url);
    }

    @JavascriptInterface
    public void onDomNotReady(final String url)
    {
      final JsSiteKeyExtractor extractor = getExtractorIfStillExist();
      if (extractor == null || !extractor.isEnabled())
      {
        return;
      }
      // doing nothing at the moment
      // still holding the lock
      Timber.d("DOM not yet ready on url %s", url);
    }
  }
}
