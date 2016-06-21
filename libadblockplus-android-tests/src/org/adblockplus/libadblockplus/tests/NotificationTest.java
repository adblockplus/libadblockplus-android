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

import android.util.Log;
import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.HeaderEntry;
import org.adblockplus.libadblockplus.LazyLogSystem;
import org.adblockplus.libadblockplus.LazyWebRequest;
import org.adblockplus.libadblockplus.Notification;
import org.adblockplus.libadblockplus.ServerResponse;
import org.adblockplus.libadblockplus.ShowNotificationCallback;
import org.adblockplus.libadblockplus.WebRequest;

import org.junit.Test;

import java.util.List;

public class NotificationTest extends BaseJsTest {

    protected FilterEngine filterEngine;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        jsEngine.setWebRequest(new LazyWebRequest());
        filterEngine = new FilterEngine(jsEngine);
    }

    protected void addNotification(String notification)
    {
        jsEngine.evaluate(
            "(function()\n" +
            "{\n" +
            "require('notification').Notification.addNotification(" + notification + ");\n" +
            "})();");
    }

    private static final String TAG = "notification";

    private class LocalShowNotificationCallback extends ShowNotificationCallback
    {
        private Notification retValue;

        public Notification getRetValue()
        {
            return retValue;
        }

        @Override
        public void showNotificationCallback(Notification notification)
        {
            Log.d(TAG, this + " received [" + notification + "]");
            retValue = notification;
        }
    }

    protected Notification peekNotification(String url) throws InterruptedException
    {
        Log.d(TAG, "Start peek");

        LocalShowNotificationCallback callback = new LocalShowNotificationCallback();
        Log.d(TAG, "set callback " + callback);
        filterEngine.setShowNotificationCallback(callback);
        filterEngine.showNextNotification(url);
        filterEngine.removeShowNotificationCallback();
        Log.d(TAG, "removed callback");
        return callback.getRetValue();
    }

    private class MockWebRequest extends WebRequest
    {
        private String responseText;

        public MockWebRequest(String responseText)
        {
            this.responseText = responseText;
        }

        @Override
        public ServerResponse httpGET(String url, List<HeaderEntry> headers)
        {
            if (url.indexOf("/notification.json") < 0)
                return new ServerResponse();

            ServerResponse response = new ServerResponse();
            response.setStatus(ServerResponse.NsStatus.OK);
            response.setResponseStatus(200);
            response.setResponse(responseText);
            return response;
        }
    }

    @Test
    public void testNoNotifications() throws InterruptedException
    {
        assertNull(peekNotification(""));
    }

    @Test
    public void testAddNotification() throws InterruptedException
    {
        addNotification(
            "{\n" +
            "   type: 'critical',\n" +
            "   title: 'testTitle',\n" +
            "   message: 'testMessage',\n" +
            "}");
        Notification notification = peekNotification("");
        assertNotNull(notification);
        assertEquals(Notification.Type.CRITICAL, notification.getType());
        assertEquals("testTitle", notification.getTitle());
        assertEquals("testMessage", notification.getMessageString());
    }

    @Test
    public void testFilterByUrl() throws InterruptedException
    {
        addNotification("{ id:'no-filter', type:'critical' }");
        addNotification("{ id:'www.com', type:'information', urlFilters:['||www.com$document'] }");
        addNotification("{ id:'www.de', type:'question', urlFilters:['||www.de$document'] }");

        Notification notification = peekNotification("");
        assertNotNull(notification);
        assertEquals(Notification.Type.CRITICAL, notification.getType());

        notification = peekNotification("http://www.de");
        assertNotNull(notification);
        assertEquals(Notification.Type.QUESTION, notification.getType());

        notification = peekNotification("http://www.com");
        assertNotNull(notification);
        assertEquals(Notification.Type.INFORMATION, notification.getType());
    }

    @Test
    public void testMarkAsShown() throws InterruptedException
    {
        addNotification("{ type: 'question' }");
        assertNotNull(peekNotification(""));

        Notification notification = peekNotification("");
        assertNotNull(notification);

        Thread.sleep(1000);
        notification.markAsShown();

        assertNull(peekNotification(""));
    }
}
