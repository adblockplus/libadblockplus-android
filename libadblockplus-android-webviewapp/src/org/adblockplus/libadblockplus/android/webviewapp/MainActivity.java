/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-2016 Eyeo GmbH
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

import org.adblockplus.libadblockplus.android.settings.AdblockHelper;
import org.adblockplus.libadblockplus.android.webview.AdblockWebView;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

public class MainActivity extends Activity
{
  public static final boolean DEVELOPMENT_BUILD = true;

  // webView can create AdblockEngine instance itself if not passed with `webView.setAdblockEngine()`
  public static final boolean USE_EXTERNAL_ADBLOCKENGINE = true;

  // adblock retain() may be long-running, pass `true` to do it in background thread
  public static final boolean ADBLOCKENGINE_RETAIN_ASYNC = true;

  private ProgressBar progress;
  private EditText url;
  private Button ok;
  private Button back;
  private Button forward;
  private Button settings;

  private AdblockWebView webView;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    bindControls();
    initControls();
  }

  private void bindControls()
  {
    url = (EditText) findViewById(R.id.main_url);
    ok = (Button) findViewById(R.id.main_ok);
    back = (Button) findViewById(R.id.main_back);
    forward = (Button) findViewById(R.id.main_forward);
    settings = (Button) findViewById(R.id.main_settings);
    progress = (ProgressBar) findViewById(R.id.main_progress);
    webView = (AdblockWebView) findViewById(R.id.main_webview);
  }

  private void setProgressVisible(boolean visible)
  {
    progress.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
  }

  private WebViewClient webViewClient = new WebViewClient()
  {
    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon)
    {
      setProgressVisible(true);

      // show updated URL (because of possible redirection)
      MainActivity.this.url.setText(url);
    }

    @Override
    public void onPageFinished(WebView view, String url)
    {
      setProgressVisible(false);
      updateButtons();
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
    {
      updateButtons();
    }
  };

  private void updateButtons()
  {
    back.setEnabled(webView.canGoBack());
    forward.setEnabled(webView.canGoForward());
  }

  private WebChromeClient webChromeClient = new WebChromeClient()
  {
    @Override
    public void onProgressChanged(WebView view, int newProgress)
    {
      progress.setProgress(newProgress);
    }
  };

  private void initControls()
  {
    ok.setOnClickListener(new View.OnClickListener()
    {
      @Override
      public void onClick(View view)
      {
        loadUrl();
      }
    });

    back.setOnClickListener(new View.OnClickListener()
    {
      @Override
      public void onClick(View v)
      {
        loadPrev();
      }
    });

    forward.setOnClickListener(new View.OnClickListener()
    {
      @Override
      public void onClick(View v)
      {
        loadForward();
      }
    });

    if (USE_EXTERNAL_ADBLOCKENGINE)
    {
      settings.setOnClickListener(new View.OnClickListener()
      {
        @Override
        public void onClick(View v)
        {
          navigateSettings();
        }
      });
    }
    else
    {
      /*
      We're able to show settings if we're using AdblockHelper facade only.
      Otherwise pass AdblockEngine instance to the fragments and not it's neither Serializable nor Parcelable.
       */
      settings.setVisibility(View.GONE);
    }

    initAdblockWebView();

    setProgressVisible(false);
    updateButtons();

    // to get debug/warning log output
    webView.setDebugMode(DEVELOPMENT_BUILD);

    // render as fast as we can
    webView.setAllowDrawDelay(0);

    // to show that external WebViewClient is still working
    webView.setWebViewClient(webViewClient);

    // to show that external WebChromeClient is still working
    webView.setWebChromeClient(webChromeClient);
  }

  private void navigateSettings()
  {
    startActivity(new Intent(this, SettingsActivity.class));
  }

  private void initAdblockWebView()
  {
    if (USE_EXTERNAL_ADBLOCKENGINE)
    {
      // external adblockEngine
      AdblockHelper.get().retain(ADBLOCKENGINE_RETAIN_ASYNC);

      if (!ADBLOCKENGINE_RETAIN_ASYNC)
      {
        webView.setAdblockEngine(AdblockHelper.get().getEngine());
      }
    }
    else
    {
      // AdblockWebView will create internal AdblockEngine instance
    }
  }

  private void hideSoftwareKeyboard()
  {
    InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
    imm.hideSoftInputFromWindow(url.getWindowToken(), 0);
  }

  private void loadPrev()
  {
    hideSoftwareKeyboard();
    if (webView.canGoBack())
    {
      webView.goBack();
    }
  }

  private void loadForward()
  {
    hideSoftwareKeyboard();
    if (webView.canGoForward())
    {
      webView.goForward();
    }
  }

  private String prepareUrl(String url)
  {
    if (!url.startsWith("http"))
      url = "http://" + url;

    // make sure url is valid URL
    return url;
  }

  private void loadUrl()
  {
    hideSoftwareKeyboard();

    // if retained with `true` we need to make sure it's ready now
    if (USE_EXTERNAL_ADBLOCKENGINE && ADBLOCKENGINE_RETAIN_ASYNC)
    {
      AdblockHelper.get().waitForReady();
      webView.setAdblockEngine(AdblockHelper.get().getEngine());
    }
    webView.loadUrl(prepareUrl(url.getText().toString()));
  }

  @Override
  protected void onDestroy()
  {
    webView.dispose(new Runnable()
    {
      @Override
      public void run()
      {
        if (USE_EXTERNAL_ADBLOCKENGINE)
        {
          AdblockHelper.get().release();
        }
      }
    });

    super.onDestroy();
  }
}
