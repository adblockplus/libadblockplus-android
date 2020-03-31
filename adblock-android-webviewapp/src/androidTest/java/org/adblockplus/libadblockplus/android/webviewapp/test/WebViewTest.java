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

package org.adblockplus.libadblockplus.android.webviewapp.test;

import android.content.Context;
import android.os.Looper;

import org.adblockplus.libadblockplus.android.settings.AdblockHelper;
import org.adblockplus.libadblockplus.android.webview.AdblockWebView;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import timber.log.Timber;

@RunWith(AndroidJUnit4.class)
public class WebViewTest
{
    private Context context = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();

    @Before
    public void setUp()
    {
        Looper.prepare();
    }

    @Rule
    public Timeout globalTimeout = Timeout.seconds(120);

    @Test
    public void createThenLoadUrlThenDispose_Test()
    {
        AdblockWebView adblockWebView;
        for(int i = 1; i <= 100; ++i)
        {
            Timber.d("Running iteration: %d", i);
            adblockWebView = new AdblockWebView(context);
            adblockWebView.setProvider(AdblockHelper.get().getProvider());
            adblockWebView.setSiteKeysConfiguration(AdblockHelper.get().getSiteKeysConfiguration());
            Timber.d("Before loadUrl()");
            adblockWebView.loadUrl("http://wp.pl");
            Timber.d("Before dispose()");
            adblockWebView.dispose(null);
            Timber.d("After dispose()");
        }
    }
}
