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

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.adblockplus.libadblockplus.android.settings.AdblockHelper;
import org.adblockplus.libadblockplus.android.webview.AdblockWebView;
import org.adblockplus.libadblockplus.android.webview.WebViewCounters;

public class TabFragment extends Fragment
{
  public static final boolean DEVELOPMENT_BUILD = true;

  // sitekeys can be used for whitelisting [Optional and have small negative impact on performance]
  public static final boolean SITEKEYS_WHITELISTING = true;

  private static final String TITLE = "title";

  private String title;
  private ProgressBar progress;
  private EditText url;
  private Button ok;
  private Button back;
  private Button forward;
  private TextView blockedCounter;
  private TextView whitelistedCounter;
  private AdblockWebView webView;

  /**
   * Factory method
   * @param title tab title
   * @return fragment instance
   */
  public static TabFragment newInstance(final String title)
  {
    final Bundle arguments = new Bundle();
    arguments.putString(TITLE, title);
    final TabFragment newFragment = new TabFragment();
    newFragment.setArguments(arguments);
    return newFragment;
  }

  @Override
  public void onCreate(final Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    this.title = getArguments().getString(TITLE);
  }

  @Override
  public View onCreateView(final LayoutInflater inflater,
                           final ViewGroup container,
                           final Bundle savedInstanceState)
  {
    final View view = inflater.inflate(R.layout.fragment_tab, container, false);
    bindControls(view);
    initControls();
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
    whitelistedCounter = rootView.findViewById(R.id.fragment_tab_wl_counter);
    webView = rootView.findViewById(R.id.fragment_tab_webview);
  }

  private void setProgressVisible(final boolean visible)
  {
    progress.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
  }

  private final WebViewClient webViewClient = new WebViewClient()
  {
    @Override
    public void onPageStarted(final WebView view, final String url, final Bitmap favicon)
    {
      setProgressVisible(true);

      // show updated URL (because of possible redirection)
      TabFragment.this.url.setText(url);
    }

    @Override
    public void onPageFinished(final WebView view, final String url)
    {
      setProgressVisible(false);
      updateButtons();
    }

    @Override
    public void onReceivedError(final WebView view, final int errorCode,
                                final String description, final String failingUrl)
    {
      updateButtons();
    }
  };

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
      progress.setProgress(newProgress);
    }
  };

  private void initControls()
  {
    url.setOnKeyListener(new View.OnKeyListener()
    {
      @Override
      public boolean onKey(final View v, final int keyCode, final KeyEvent event)
      {
        if (event.getAction() == KeyEvent.ACTION_DOWN &&
            keyCode == KeyEvent.KEYCODE_ENTER)
        {
          loadUrl();
        }
        return false;
      }
    });

    ok.setOnClickListener(new View.OnClickListener()
    {
      @Override
      public void onClick(final View view)
      {
        loadUrl();
      }
    });

    back.setOnClickListener(new View.OnClickListener()
    {
      @Override
      public void onClick(final View v)
      {
        loadPrev();
      }
    });

    forward.setOnClickListener(new View.OnClickListener()
    {
      @Override
      public void onClick(final View v)
      {
        TabFragment.this.loadForward();
      }
    });

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
        public void onWhitelistedChanged(final int newValue)
        {
          whitelistedCounter.setText(String.valueOf(newValue));
        }
      });
    webView.setEventsListener(eventsListener);

    setProgressVisible(false);
    updateButtons();

    // to get debug/warning log output
    webView.setDebugMode(DEVELOPMENT_BUILD);

    // to show that external WebViewClient is still working
    webView.setWebViewClient(webViewClient);

    // to show that external WebChromeClient is still working
    webView.setWebChromeClient(webChromeClient);
  }

  private void initAdblockWebView()
  {
    // use shared filters data (not to increase memory consumption)
    webView.setProvider(AdblockHelper.get().getProvider());

    if (SITEKEYS_WHITELISTING)
    {
      webView.setSiteKeysConfiguration(AdblockHelper.get().getSiteKeysConfiguration());
    }
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
