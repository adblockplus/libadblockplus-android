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

import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.LazyWebRequest;
import org.adblockplus.libadblockplus.Notification;
import org.adblockplus.libadblockplus.ShowNotificationCallback;

import org.junit.Test;

public class NotificationTest extends BaseJsTest
{
  protected FilterEngine filterEngine;

  @Override
  protected void setUp() throws Exception
  {
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
      retValue = notification;
    }
  }

  protected Notification peekNotification(String url) throws InterruptedException
  {
    LocalShowNotificationCallback callback = new LocalShowNotificationCallback();
    filterEngine.setShowNotificationCallback(callback);
    filterEngine.showNextNotification(url);
    filterEngine.removeShowNotificationCallback();
    return callback.getRetValue();
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
    addNotification("{ id: 'id', type: 'information' }");
    assertNotNull(peekNotification(""));

    Notification notification = peekNotification("");
    assertNotNull(notification);

    Thread.sleep(1000);
    notification.markAsShown();

    assertNull(peekNotification(""));
  }
}
