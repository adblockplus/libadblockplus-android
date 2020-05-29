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

import org.adblockplus.libadblockplus.android.settings.AdblockHelper;
import org.adblockplus.libadblockplus.android.webview.AdblockWebView;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import androidx.test.annotation.UiThreadTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.UiThreadTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import timber.log.Timber;

@RunWith(AndroidJUnit4.class)
public class WebViewTest
{
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();

    @Rule
    public final Timeout globalTimeout = Timeout.seconds(150);

    @Rule
    public final UiThreadTestRule uiThreadTestRule = new UiThreadTestRule();

    private void useAdblockWebViewThenDispose()
    {
        final AdblockWebView adblockWebView = new AdblockWebView(context);
        adblockWebView.setProvider(AdblockHelper.get().getProvider());
        adblockWebView.setSiteKeysConfiguration(AdblockHelper.get().getSiteKeysConfiguration());
        Timber.d("Before loadUrl()");
        adblockWebView.loadUrl("http://wp.pl");
        Timber.d("Before dispose()");
        adblockWebView.dispose(null);
        Timber.d("After dispose()");
    }

    @Test
    @UiThreadTest
    public void testCreateThenLoadUrlThenDispose()
    {
        for (int i = 1; i <= 100; ++i)
        {
            Timber.d("Running iteration: %d", i);
            useAdblockWebViewThenDispose();
        }
    }

    // This is the same test as createThenLoadUrlThenDispose_Test() but with extra retain()/release()
    // at the beginning/end of each loop iteration.
    @Test
    @UiThreadTest
    public void testRetainThenCreateThenLoadUrlThenDisposeThenRelease()
    {
        for (int i = 1; i <= 100; ++i)
        {
            Timber.d("Running iteration: %d", i);
            AdblockHelper.get().getProvider().retain(true);
            useAdblockWebViewThenDispose();
            AdblockHelper.get().getProvider().release();
        }
    }
}
