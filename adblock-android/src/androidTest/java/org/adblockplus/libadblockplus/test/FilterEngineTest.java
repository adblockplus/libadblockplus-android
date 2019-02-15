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

package org.adblockplus.libadblockplus.test;

import org.adblockplus.libadblockplus.Filter;
import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.Subscription;
import org.adblockplus.libadblockplus.TestFilterChangeCallback;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FilterEngineTest extends BaseFilterEngineTest
{
  private static final String[] EMPTY_ARRAY = new String[]{};

  protected void removeSubscriptions()
  {
    while (filterEngine.getListedSubscriptions().size() > 0)
    {
      filterEngine.getListedSubscriptions().get(0).removeFromList();
    }
  }

  @Test
  public void testFilterCreation()
  {
    final Filter filter1 = filterEngine.getFilter("foo");
    assertEquals(Filter.Type.BLOCKING, filter1.getType());
    assertEquals(Filter.Type.EXCEPTION, filterEngine.getFilter("@@foo").getType());
    assertEquals(Filter.Type.ELEMHIDE, filterEngine.getFilter("example.com##foo").getType());
    assertEquals(Filter.Type.ELEMHIDE_EXCEPTION, filterEngine.getFilter("example.com#@#foo").getType());
    final Filter filter5 = filterEngine.getFilter("  foo  ");
    assertEquals(filter1, filter5);
  }

  @Test
  public void testFilterProperties()
  {
    final Filter filter = filterEngine.getFilter("foo");

    assertTrue(filter.getProperty("stringFoo").isUndefined());
    assertTrue(filter.getProperty("intFoo").isUndefined());
    assertTrue(filter.getProperty("boolFoo").isUndefined());

    filter.setProperty("stringFoo", jsEngine.newValue("y"));
    filter.setProperty("intFoo", jsEngine.newValue(24L));
    filter.setProperty("boolFoo", jsEngine.newValue(true));
    assertEquals("y", filter.getProperty("stringFoo").asString());
    assertEquals(24L, filter.getProperty("intFoo").asLong());
    assertTrue(filter.getProperty("boolFoo").asBoolean());
  }

  @Test
  public void testAddRemoveFilters()
  {
    assertEquals(0, filterEngine.getListedFilters().size());
    final Filter filter = filterEngine.getFilter("foo");
    assertEquals(0, filterEngine.getListedFilters().size());
    assertFalse(filter.isListed());
    filter.addToList();
    assertEquals(1, filterEngine.getListedFilters().size());
    assertEquals(filter, filterEngine.getListedFilters().get(0));
    assertTrue(filter.isListed());
    filter.addToList();
    assertEquals(1, filterEngine.getListedFilters().size());
    assertEquals(filter, filterEngine.getListedFilters().get(0));
    assertTrue(filter.isListed());
    filter.removeFromList();
    assertEquals(0, filterEngine.getListedFilters().size());
    assertFalse(filter.isListed());
    filter.removeFromList();
    assertEquals(0, filterEngine.getListedFilters().size());
    assertFalse(filter.isListed());
  }

  @Test
  public void testSubscriptionProperties()
  {
    final Subscription subscription = filterEngine.getSubscription("foo");

    assertTrue(subscription.getProperty("stringFoo").isUndefined());
    assertTrue(subscription.getProperty("intFoo").isUndefined());
    assertTrue(subscription.getProperty("boolFoo").isUndefined());

    subscription.setProperty("stringFoo", jsEngine.newValue("y"));
    subscription.setProperty("intFoo", jsEngine.newValue(24L));
    subscription.setProperty("boolFoo", jsEngine.newValue(true));
    assertEquals("y", subscription.getProperty("stringFoo").asString());
    assertEquals(24L, subscription.getProperty("intFoo").asLong());
    assertTrue(subscription.getProperty("boolFoo").asBoolean());
  }

  @Test
  public void testAddedSubscriptionIsEnabled()
  {
    final Subscription subscription = filterEngine.getSubscription("foo");
    assertFalse(subscription.isDisabled());
  }

  @Test
  public void testDisablingSubscriptionDisablesItAndFiresEvent()
  {
    final Subscription subscription = filterEngine.getSubscription("foo");
    final TestFilterChangeCallback callback = new TestFilterChangeCallback();
    filterEngine.setFilterChangeCallback(callback);
    assertFalse(subscription.isDisabled());
    subscription.setDisabled(true);
    final List<TestFilterChangeCallback.Event> filteredEvents = new ArrayList();
    for (TestFilterChangeCallback.Event event : callback.getEvents())
    {
      if (!event.getAction().equals("subscription.disabled") ||
          !event.getJsValue().getProperty("url").asString().equals("foo"))
      {
        continue;
      }
      else
      {
        filteredEvents.add(event);
      }
    }
    assertEquals(1, filteredEvents.size());
    assertTrue(subscription.isDisabled());
  }

  @Test
  public void testEnablingSubscriptionEnablesItAndFiresEvent()
  {
    final Subscription subscription = filterEngine.getSubscription("foo");
    assertFalse(subscription.isDisabled());
    subscription.setDisabled(true);
    assertTrue(subscription.isDisabled());

    final TestFilterChangeCallback callback = new TestFilterChangeCallback();
    filterEngine.setFilterChangeCallback(callback);
    subscription.setDisabled(false);
    final List<TestFilterChangeCallback.Event> filteredEvents = new ArrayList();
    for (TestFilterChangeCallback.Event event : callback.getEvents())
    {
      if (!event.getAction().equals("subscription.disabled") ||
          !event.getJsValue().getProperty("url").asString().equals("foo"))
      {
        continue;
      }
      else
      {
        filteredEvents.add(event);
      }
    }
    assertEquals(1, filteredEvents.size());
    assertFalse(subscription.isDisabled());
  }

  @Test
  public void testAddRemoveSubscriptions()
  {
    removeSubscriptions();

    assertEquals(0, filterEngine.getListedSubscriptions().size());
    final Subscription subscription = filterEngine.getSubscription("foo");
    assertEquals(0, filterEngine.getListedSubscriptions().size());
    assertFalse(subscription.isListed());
    subscription.addToList();
    assertEquals(1, filterEngine.getListedSubscriptions().size());
    assertEquals(subscription, filterEngine.getListedSubscriptions().get(0));
    assertTrue(subscription.isListed());
    subscription.addToList();
    assertEquals(1, filterEngine.getListedSubscriptions().size());
    assertEquals(subscription, filterEngine.getListedSubscriptions().get(0));
    assertTrue(subscription.isListed());
    subscription.removeFromList();
    assertEquals(0, filterEngine.getListedSubscriptions().size());
    assertFalse(subscription.isListed());
    subscription.removeFromList();
    assertEquals(0, filterEngine.getListedSubscriptions().size());
    assertFalse(subscription.isListed());
  }

  @Test
  public void testSubscriptionUpdates()
  {
    final Subscription subscription = filterEngine.getSubscription("foo");
    assertFalse(subscription.isUpdating());
    subscription.updateFilters();
  }

  @Test
  public void testMatches()
  {
    filterEngine.getFilter("adbanner.gif").addToList();
    filterEngine.getFilter("@@notbanner.gif").addToList();
    filterEngine.getFilter("tpbanner.gif$third-party").addToList();
    filterEngine.getFilter("fpbanner.gif$~third-party").addToList();
    filterEngine.getFilter("combanner.gif$domain=example.com").addToList();
    filterEngine.getFilter("orgbanner.gif$domain=~example.com").addToList();

    final Filter match1 = filterEngine.matches("http://example.org/foobar.gif", FilterEngine.ContentType.IMAGE, "");
    assertNull(match1);

    final Filter match2 = filterEngine.matches("http://example.org/adbanner.gif", FilterEngine.ContentType.IMAGE, "");
    assertNotNull(match2);
    assertEquals(Filter.Type.BLOCKING, match2.getType());

    final Filter match3 = filterEngine.matches("http://example.org/notbanner.gif", FilterEngine.ContentType.IMAGE, "");
    assertNotNull(match3);
    assertEquals(Filter.Type.EXCEPTION, match3.getType());

    final Filter match4 = filterEngine.matches("http://example.org/notbanner.gif", FilterEngine.ContentType.IMAGE, "");
    assertNotNull(match4);
    assertEquals(Filter.Type.EXCEPTION, match4.getType());

    final Filter match5 = filterEngine.matches("http://example.org/tpbanner.gif", FilterEngine.ContentType.IMAGE, "http://example.org/");
    assertNull(match5);

    final Filter match6 = filterEngine.matches("http://example.org/fpbanner.gif", FilterEngine.ContentType.IMAGE, "http://example.org/");
    assertNotNull(match6);
    assertEquals(Filter.Type.BLOCKING, match6.getType());

    final Filter match7 = filterEngine.matches("http://example.org/tpbanner.gif", FilterEngine.ContentType.IMAGE, "http://example.com/");
    assertNotNull(match7);
    assertEquals(Filter.Type.BLOCKING, match7.getType());

    final Filter match8 = filterEngine.matches("http://example.org/fpbanner.gif", FilterEngine.ContentType.IMAGE, "http://example.com/");
    assertNull(match8);

    final Filter match9 = filterEngine.matches("http://example.org/combanner.gif", FilterEngine.ContentType.IMAGE, "http://example.com/");
    assertNotNull(match9);
    assertEquals(Filter.Type.BLOCKING, match9.getType());

    final Filter match10 = filterEngine.matches("http://example.org/combanner.gif", FilterEngine.ContentType.IMAGE, "http://example.org/");
    assertNull(match10);

    final Filter match11 = filterEngine.matches("http://example.org/orgbanner.gif", FilterEngine.ContentType.IMAGE, "http://example.com/");
    assertNull(match11);

    final Filter match12 = filterEngine.matches("http://example.org/orgbanner.gif", FilterEngine.ContentType.IMAGE, "http://example.org/");
    assertNotNull(match12);
    assertEquals(Filter.Type.BLOCKING, match12.getType());
  }

  @Test
  public void testMatchesOnWhitelistedDomain()
  {
    filterEngine.getFilter("adbanner.gif").addToList();
    filterEngine.getFilter("@@||example.org^$document").addToList();

    final Filter match1 =
      filterEngine.matches("http://ads.com/adbanner.gif", FilterEngine.ContentType.IMAGE,
      "http://example.com/");
    assertNotNull(match1);
    assertEquals(Filter.Type.BLOCKING, match1.getType());

    final Filter match2 =
      filterEngine.matches("http://ads.com/adbanner.gif", FilterEngine.ContentType.IMAGE,
      "http://example.org/");
    assertNotNull(match2);
    assertEquals(Filter.Type.EXCEPTION, match2.getType());
  }

  @Test
  public void testMatchesWithContentTypeMask()
  {
    filterEngine.getFilter("adbanner.gif.js$script,image").addToList();
    filterEngine.getFilter("@@notbanner.gif").addToList();
    filterEngine.getFilter("blockme").addToList();
    filterEngine.getFilter("@@||example.doc^$document").addToList();
    filterEngine.getFilter("||popexample.com^$popup").addToList();

    assertNull("another url should not match",
        filterEngine.matches("http://example.org/foobar.gif",
        FilterEngine.ContentType.IMAGE, ""));

    assertNull("zero mask should not match (filter with some options)",
        filterEngine.matches("http://example.org/adbanner.gif.js",
        /*mask*/ FilterEngine.ContentType.maskOf(), ""));

    assertNull("zero mask should not match (filter without any option)",
        filterEngine.matches("http://example.xxx/blockme",
        /*mask*/ FilterEngine.ContentType.maskOf(), ""));

    assertNull("one arbitrary flag in mask should not match",
        filterEngine.matches("http://example.org/adbanner.gif.js",
            FilterEngine.ContentType.OBJECT, ""));

    assertNotNull("one of flags in mask should match",
        filterEngine.matches("http://example.org/adbanner.gif.js",
            FilterEngine.ContentType.maskOf(
                FilterEngine.ContentType.IMAGE,
                FilterEngine.ContentType.OBJECT),
            ""));

    assertNotNull("both flags in mask should match",
        filterEngine.matches("http://example.org/adbanner.gif.js",
            FilterEngine.ContentType.maskOf(
                FilterEngine.ContentType.IMAGE,
                FilterEngine.ContentType.SCRIPT),
            ""));

    assertNotNull("both flags with another flag in mask should match",
        filterEngine.matches("http://example.org/adbanner.gif.js",
        FilterEngine.ContentType.maskOf(
            FilterEngine.ContentType.IMAGE,
            FilterEngine.ContentType.SCRIPT,
            FilterEngine.ContentType.OBJECT), ""));

    assertNotNull("one of flags in mask should match",
        filterEngine.matches("http://example.org/adbanner.gif.js",
            FilterEngine.ContentType.maskOf(
                FilterEngine.ContentType.SCRIPT,
                FilterEngine.ContentType.OBJECT),
            ""));

    {
      final Filter filter = filterEngine.matches("http://child.any/blockme",
          FilterEngine.ContentType.maskOf(
              FilterEngine.ContentType.SCRIPT,
              FilterEngine.ContentType.OBJECT),
          "http://example.doc");
      assertNotNull("non-zero mask should match on whitelisted document", filter);
      assertEquals(Filter.Type.EXCEPTION, filter.getType());
    }

    {
      final Filter filter = filterEngine.matches("http://example.doc/blockme",
          /*mask*/ FilterEngine.ContentType.maskOf(), "http://example.doc");
      assertNotNull("zero mask should match when document is whitelisted", filter);
      assertEquals(Filter.Type.EXCEPTION, filter.getType());
    }
  }

  @Test
  public void testMatchesNestedFrameRequest()
  {
    filterEngine.getFilter("adbanner.gif").addToList();
    filterEngine.getFilter("@@adbanner.gif$domain=example.org").addToList();

    final String[] documentUrls1 =
      {
        "http://ads.com/frame/",
        "http://example.com/"
      };
    final Filter match1 =
      filterEngine.matches("http://ads.com/adbanner.gif", FilterEngine.ContentType.IMAGE,
      documentUrls1);
    assertNotNull(match1);
    assertEquals(Filter.Type.BLOCKING, match1.getType());

    final String[] documentUrls2 = { "http://ads.com/frame/", "http://example.org/" };
    final Filter match2 =
      filterEngine.matches("http://ads.com/adbanner.gif", FilterEngine.ContentType.IMAGE,
      documentUrls2);
    assertNotNull(match2);
    assertEquals(Filter.Type.EXCEPTION, match2.getType());

    final String[] documentUrls3 =
      {
        "http://example.org/",
        "http://ads.com/frame/"
      };
    final Filter match3 =
      filterEngine.matches("http://ads.com/adbanner.gif", FilterEngine.ContentType.IMAGE,
      documentUrls3);
    assertNotNull(match3);
    assertEquals(Filter.Type.BLOCKING, match3.getType());
  }

  @Test
  public void testMatchesNestedFrameOnWhitelistedDomain()
  {
    filterEngine.getFilter("adbanner.gif").addToList();
    filterEngine.getFilter("@@||example.org^$document,domain=ads.com").addToList();

    final String[] documentUrls1 =
      {
        "http://ads.com/frame/",
        "http://example.com/"
      };
    final Filter match1 =
      filterEngine.matches("http://ads.com/adbanner.gif", FilterEngine.ContentType.IMAGE,
      documentUrls1);
    assertNotNull(match1);
    assertEquals(Filter.Type.BLOCKING, match1.getType());

    final String[] documentUrls2 =
      {
        "http://ads.com/frame/",
        "http://example.org/"
      };
    final Filter match2 =
      filterEngine.matches("http://ads.com/adbanner.gif", FilterEngine.ContentType.IMAGE,
      documentUrls2);
    assertNotNull(match2);
    assertEquals(Filter.Type.EXCEPTION, match2.getType());

    final String[] documentUrls3 =
      {
        "http://example.org/"
      };
    final Filter match3 =
      filterEngine.matches("http://ads.com/adbanner.gif", FilterEngine.ContentType.IMAGE,
      documentUrls3);
    assertNotNull(match3);
    assertEquals(Filter.Type.BLOCKING, match3.getType());

    final String[] documentUrls4 =
      {
        "http://example.org/",
        "http://ads.com/frame/"
      };
    final Filter match4 =
      filterEngine.matches("http://ads.com/adbanner.gif", FilterEngine.ContentType.IMAGE,
      documentUrls4);
    assertNotNull(match4);
    assertEquals(Filter.Type.BLOCKING, match4.getType());

    final String[] documentUrls5 =
      {
        "http://ads.com/frame/",
        "http://example.org/",
        "http://example.com/"
      };
    final Filter match5 =
      filterEngine.matches("http://ads.com/adbanner.gif", FilterEngine.ContentType.IMAGE,
      documentUrls5);
    assertNotNull(match5);
    assertEquals(Filter.Type.EXCEPTION, match5.getType());
  }

  @Test
  public void testFirstRunFlag()
  {
    assertTrue(filterEngine.isFirstRun());
  }

  @Test
  public void testSetRemoveFilterChangeCallback()
  {
    final TestFilterChangeCallback callback = new TestFilterChangeCallback();
    filterEngine.setFilterChangeCallback(callback);
    callback.getEvents().clear();
    filterEngine.getFilter("foo").addToList();
    final int eventsCount = callback.getEvents().size();

    // we want to actually check the call count didn't change.
    filterEngine.removeFilterChangeCallback();
    filterEngine.getFilter("foo").removeFromList();
    assertEquals(eventsCount, callback.getEvents().size());
  }

  @Test
  public void testDocumentWhitelisting()
  {
    filterEngine.getFilter("@@||example.org^$document").addToList();
    filterEngine.getFilter("@@||example.com^$document,domain=example.de").addToList();

    final String[] emptyArray = {};
    assertTrue(filterEngine.isDocumentWhitelisted("http://example.org", emptyArray));
    assertFalse(filterEngine.isDocumentWhitelisted("http://example.co.uk", emptyArray));
    assertFalse(filterEngine.isDocumentWhitelisted("http://example.com", emptyArray));

    final String[] documentUrls1 = { "http://example.de" };
    assertTrue(filterEngine.isDocumentWhitelisted("http://example.com", documentUrls1));
    assertFalse(filterEngine.isDocumentWhitelisted("http://example.co.uk", documentUrls1));
  }

  @Test
  public void testElemhideWhitelisting()
  {
    filterEngine.getFilter("@@||example.org^$elemhide").addToList();
    filterEngine.getFilter("@@||example.com^$elemhide,domain=example.de").addToList();

    assertTrue(filterEngine.isElemhideWhitelisted("http://example.org", EMPTY_ARRAY));
    assertFalse(filterEngine.isElemhideWhitelisted("http://example.co.uk", EMPTY_ARRAY));
    assertFalse(filterEngine.isElemhideWhitelisted("http://example.com", EMPTY_ARRAY));

    final String[] documentUrls1 = { "http://example.de" };
    assertTrue(filterEngine.isElemhideWhitelisted("http://example.com", documentUrls1));
    assertFalse(filterEngine.isElemhideWhitelisted("http://example.co.uk", documentUrls1));
  }

  @Test
  public void testElementHidingSelectorsListEmpty()
  {
    final List<String> sels = filterEngine.getElementHidingSelectors("example.org");
    assertTrue(sels.isEmpty());
  }

  @Test
  public void testElementHidingSelectorsList()
  {
    final String[] filters =
      {
        // other type of filters
        "/testcasefiles/blocking/addresspart/abptestcasepath/",
        "example.org#?#div:-abp-properties(width: 213px)",

        // element hiding selectors
        "###testcase-eh-id",
        "example.org###testcase-eh-id",
        "example.org##.testcase-eh-class",
        "example.org##.testcase-container > .testcase-eh-descendant",
        "~foo.example.org,example.org##foo",
        "~othersiteneg.org##testneg",

        // other site
        "othersite.com###testcase-eh-id"
      };

    for (final String filter : filters)
    {
      filterEngine.getFilter(filter).addToList();
    }

    final List<String> sels = filterEngine.getElementHidingSelectors("example.org");

    assertNotNull(sels);
    assertEquals(6, sels.size());
    assertEquals("#testcase-eh-id", sels.get(0));
    assertEquals("#testcase-eh-id", sels.get(1));
    assertEquals(".testcase-eh-class", sels.get(2));
    assertEquals(".testcase-container > .testcase-eh-descendant", sels.get(3));
    assertEquals("foo", sels.get(4));
    assertEquals("testneg", sels.get(5));
  }

  @Test
  public void testElementHidingSelectorsListSingleGeneric()
  {
    // element hiding selectors
    filterEngine.getFilter("###testcase-eh-id").addToList();

    final List<String> sels = filterEngine.getElementHidingSelectors("");

    assertNotNull(sels);
    assertEquals(1, sels.size());
    assertEquals("#testcase-eh-id", sels.get(0));
  }

  @Test
  public void testElementHidingSelectorsListSingleDomain()
  {
    // element hiding selectors
    filterEngine.getFilter("example.org##.testcase - eh - class").addToList();

    final List<String> sels = filterEngine.getElementHidingSelectors("example.org");

    assertNotNull(sels);
    assertEquals(1, sels.size());
    assertEquals(".testcase - eh - class", sels.get(0));
  }

  @Test
  public void testElementHidingSelectorsListDup()
  {
    // element hiding selectors - duplicates
    filterEngine.getFilter("example.org###dup").addToList();
    filterEngine.getFilter("example.org###dup").addToList();
    filterEngine.getFilter("othersite.org###dup").addToList();

    final List<String> sels = filterEngine.getElementHidingSelectors("example.org");

    // no dups
    assertNotNull(sels);
    assertEquals(1, sels.size());
    assertEquals("#dup", sels.get(0));

    // this makes duplicates
    filterEngine.getFilter("~foo.example.org,example.org###dup").addToList();
    filterEngine.getFilter("~bar.example.org,example.org###dup").addToList();

    final List<String> selsDup = filterEngine.getElementHidingSelectors("example.org");

    // dups
    assertNotNull(selsDup);
    assertEquals(3, selsDup.size());
    assertEquals("#dup", selsDup.get(0));
    assertEquals("#dup", selsDup.get(1));
    assertEquals("#dup", selsDup.get(2));

    final List<String> selsBar = filterEngine.getElementHidingSelectors("bar.example.org");
    assertNotNull(selsBar);
    assertEquals(2, selsBar.size());
    assertEquals("#dup", selsBar.get(0));
    assertEquals("#dup", selsBar.get(1));
  }

  @Test
  public void testElementHidingSelectorsListDiff()
  {
    filterEngine.getFilter("example1.org###testcase-eh-id").addToList();
    filterEngine.getFilter("example2.org###testcase-eh-id").addToList();

    final List<String> sels1 = filterEngine.getElementHidingSelectors("example1.org");
    assertEquals(1, sels1.size());
    assertEquals("#testcase-eh-id", sels1.get(0));

    final List<String> sels2 = filterEngine.getElementHidingSelectors("example2.org");
    assertEquals(1, sels2.size());
    assertEquals("#testcase-eh-id", sels2.get(0));

    final List<String> selsGen = filterEngine.getElementHidingSelectors("");
    assertTrue(selsGen.isEmpty());

    final List<String> selsNonExisting = filterEngine.getElementHidingSelectors("non-existing-domain.com");
    assertTrue(selsNonExisting.isEmpty());
  }
}
