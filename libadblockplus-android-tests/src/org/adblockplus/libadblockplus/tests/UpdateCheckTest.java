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

package org.adblockplus.libadblockplus.tests;

import org.adblockplus.libadblockplus.AppInfo;
import org.adblockplus.libadblockplus.EventCallback;
import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.HeaderEntry;
import org.adblockplus.libadblockplus.JsEngine;
import org.adblockplus.libadblockplus.JsValue;
import org.adblockplus.libadblockplus.LazyLogSystem;
import org.adblockplus.libadblockplus.LazyWebRequest;
import org.adblockplus.libadblockplus.ServerResponse;
import org.adblockplus.libadblockplus.UpdateCheckDoneCallback;

import org.junit.Test;

import java.util.List;

public class UpdateCheckTest extends BaseJsTest
{
    protected String previousRequestUrl;

    public class TestWebRequest extends LazyWebRequest
    {
        public ServerResponse response = new ServerResponse();

        @Override
        public ServerResponse httpGET(String url, List<HeaderEntry> headers)
        {
            if (url.indexOf("easylist") >= 0)
            {
              return super.httpGET(url, headers);
            }

            previousRequestUrl = url;
            return response;
        }
    }

    protected AppInfo appInfo;
    protected TestWebRequest webRequest;
    protected JsEngine jsEngine;
    protected FilterEngine filterEngine;

    protected boolean eventCallbackCalled;
    protected List<JsValue> eventCallbackParams;
    protected boolean updateCallbackCalled;
    protected String updateError;

    private EventCallback eventCallback = new EventCallback()
    {
        @Override
        public void eventCallback(List<JsValue> params)
        {
            eventCallbackCalled = true;
            eventCallbackParams = params;
        }
    };

    private UpdateCheckDoneCallback updateCallback = new UpdateCheckDoneCallback()
    {
        @Override
        public void updateCheckDoneCallback(String error)
        {
            updateCallbackCalled = true;
            updateError = error;
        }
    };

    public void reset()
    {
        jsEngine = new JsEngine(appInfo);
        jsEngine.setLogSystem(new LazyLogSystem());
        jsEngine.setDefaultFileSystem(getContext().getFilesDir().getAbsolutePath());
        jsEngine.setWebRequest(webRequest);
        jsEngine.setEventCallback("updateAvailable", eventCallback);

        filterEngine = new FilterEngine(jsEngine);
    }

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        appInfo = AppInfo.builder().build();
        webRequest = new TestWebRequest();
        eventCallbackCalled = false;
        updateCallbackCalled = false;
        reset();
    }

    public void forceUpdateCheck()
    {
        filterEngine.forceUpdateCheck(updateCallback);
    }

    @Test
    public void testRequestFailure() throws InterruptedException
    {
        webRequest.response.setStatus(ServerResponse.NsStatus.ERROR_FAILURE);

        appInfo = AppInfo
            .builder()
            .setName("1")
            .setVersion("3")
            .setApplication("4")
            .setApplicationVersion("2")
            .setDevelopmentBuild(false)
            .build();

        reset();
        forceUpdateCheck();

        Thread.sleep(100);

        assertFalse(eventCallbackCalled);
        assertTrue(updateCallbackCalled);
        assertNotNull(updateError);

        String expectedUrl = filterEngine.getPref("update_url_release").asString();
        String platform = "libadblockplus";
        String platformVersion = "1.0";

        expectedUrl = expectedUrl
            .replaceAll("%NAME%", appInfo.name)
            .replaceAll("%TYPE%", "1"); // manual update

        expectedUrl +=
            "&addonName=" + appInfo.name +
            "&addonVersion=" + appInfo.version +
            "&application=" + appInfo.application +
            "&applicationVersion=" + appInfo.applicationVersion +
            "&platform=" + platform +
            "&platformVersion=" + platformVersion +
            "&lastVersion=0&downloadCount=0";

        assertEquals(expectedUrl, previousRequestUrl);
    }

    @Test
    public void testApplicationUpdateAvailable() throws InterruptedException
    {
        webRequest.response.setStatus(ServerResponse.NsStatus.OK);
        webRequest.response.setResponseStatus(200);
        webRequest.response.setResponse(
            "{\"1/4\": {\"version\":\"3.1\",\"url\":\"https://foo.bar/\"}}");

        appInfo = AppInfo
            .builder()
            .setName("1")
            .setVersion("3")
            .setApplication("4")
            .setApplicationVersion("2")
            .setDevelopmentBuild(true)
            .build();

        reset();
        forceUpdateCheck();

        Thread.sleep(1000);

        assertTrue(eventCallbackCalled);
        assertNotNull(eventCallbackParams);
        assertEquals(1l, eventCallbackParams.size());
        assertEquals("https://foo.bar/", eventCallbackParams.get(0).asString());
        assertTrue(updateCallbackCalled);
        assertEquals("", updateError);
    }

    @Test
    public void testWrongApplication() throws InterruptedException
    {
        webRequest.response.setStatus(ServerResponse.NsStatus.OK);
        webRequest.response.setResponseStatus(200);
        webRequest.response.setResponse(
            "{\"1/3\": {\"version\":\"3.1\",\"url\":\"https://foo.bar/\"}}");

        appInfo = AppInfo
            .builder()
            .setName("1")
            .setVersion("3")
            .setApplication("4")
            .setApplicationVersion("2")
            .setDevelopmentBuild(true)
            .build();

        reset();
        forceUpdateCheck();

        Thread.sleep(1000);

        assertFalse(eventCallbackCalled);
        assertTrue(updateCallbackCalled);
        assertEquals("", updateError);
    }

    @Test
    public void testWrongVersion() throws InterruptedException
    {
        webRequest.response.setStatus(ServerResponse.NsStatus.OK);
        webRequest.response.setResponseStatus(200);
        webRequest.response.setResponse(
            "{\"1\": {\"version\":\"3\",\"url\":\"https://foo.bar/\"}}");

        appInfo = AppInfo
            .builder()
            .setName("1")
            .setVersion("3")
            .setApplication("4")
            .setApplicationVersion("2")
            .setDevelopmentBuild(true)
            .build();

        reset();
        forceUpdateCheck();

        Thread.sleep(1000);

        assertFalse(eventCallbackCalled);
        assertTrue(updateCallbackCalled);
        assertEquals("", updateError);
    }

    @Test
    public void testWrongURL() throws InterruptedException
    {
        webRequest.response.setStatus(ServerResponse.NsStatus.OK);
        webRequest.response.setResponseStatus(200);
        webRequest.response.setResponse(
            "{\"1\": {\"version\":\"3.1\",\"url\":\"http://insecure/\"}}");

        appInfo = AppInfo
            .builder()
            .setName("1")
            .setVersion("3")
            .setApplication("4")
            .setApplicationVersion("2")
            .setDevelopmentBuild(true)
            .build();

        reset();
        forceUpdateCheck();

        Thread.sleep(1000);

        assertFalse(eventCallbackCalled);
        assertTrue(updateCallbackCalled);
        assertTrue(updateError.length() > 0);
    }
}
