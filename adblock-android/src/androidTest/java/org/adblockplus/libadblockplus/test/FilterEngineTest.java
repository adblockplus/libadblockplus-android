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

import org.adblockplus.ContentType;
import org.adblockplus.EmulationSelector;
import org.adblockplus.Filter;
import org.adblockplus.Subscription;
import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.TestFilterChangeCallback;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.adblockplus.ContentType.maskOf;

public class FilterEngineTest extends BaseFilterEngineTest
{
  private static final String SITEKEY = "cNAQEBBQADSwAwSAJBAJRmzcpTevQqkWn6dJuX";

  @Override
  public void setUp()
  {
    // The tests here do not need HttpClient at all
    setUpHttpClient(null);
    super.setUp();
  }

  protected void removeSubscriptions()
  {
    while (filterEngine.getListedSubscriptions().size() > 0)
    {
      final Subscription subscription = filterEngine.getListedSubscriptions().get(0);
      filterEngine.removeSubscription(subscription);
    }
  }

  @Test
  public void testFilterCreation()
  {
    final Filter filter1 = filterEngine.getFilterFromText("foo");
    assertEquals(Filter.Type.BLOCKING, filter1.type);
    assertEquals(Filter.Type.EXCEPTION, filterEngine.getFilterFromText("@@foo").type);
    assertEquals(Filter.Type.ELEMHIDE, filterEngine.getFilterFromText("example.com##foo").type);
    assertEquals(Filter.Type.ELEMHIDE_EXCEPTION,
        filterEngine.getFilterFromText("example.com#@#foo").type);
    final Filter filter5 = filterEngine.getFilterFromText("  foo  ");
    assertEquals(filter1, filter5);
    final Filter filter6 = filterEngine.getFilterFromText("example.org#?#foo");
    assertEquals(Filter.Type.ELEMHIDE_EMULATION, filter6.type);
  }

  @Test
  public void testAddRemoveFiltersLegacy()
  {
    assertEquals(0, filterEngine.getListedFilters().size());
    final Filter filter = filterEngine.getFilterFromText("foo");
    assertEquals(0, filterEngine.getListedFilters().size());
    assertFalse(filterEngine.getListedFilters().contains(filter));
    filterEngine.addFilter(filter);
    assertEquals(1, filterEngine.getListedFilters().size());
    assertEquals(filter, filterEngine.getListedFilters().get(0));
    assertTrue(filterEngine.getListedFilters().contains(filter));
    filterEngine.addFilter(filter);
    assertEquals(1, filterEngine.getListedFilters().size());
    assertEquals(filter, filterEngine.getListedFilters().get(0));
    assertTrue(filterEngine.getListedFilters().contains(filter));
    filterEngine.removeFilter(filter);
    assertEquals(0, filterEngine.getListedFilters().size());
    assertFalse(filterEngine.getListedFilters().contains(filter));
    filterEngine.removeFilter(filter);
    assertEquals(0, filterEngine.getListedFilters().size());
    assertFalse(filterEngine.getListedFilters().contains(filter));
  }

  @Test
  public void testAddRemoveFilters()
  {
    assertEquals(0, filterEngine.getListedFilters().size());
    final Filter filter = filterEngine.getFilterFromText("foo");
    assertEquals(0, filterEngine.getListedFilters().size());
    filterEngine.addFilter(filter);
    assertEquals(1, filterEngine.getListedFilters().size());
    assertEquals(filter, filterEngine.getListedFilters().get(0));
    filterEngine.addFilter(filter);
    assertEquals(1, filterEngine.getListedFilters().size());
    assertEquals(filter, filterEngine.getListedFilters().get(0));
    filterEngine.removeFilter(filter);
    assertEquals(0, filterEngine.getListedFilters().size());
    filterEngine.removeFilter(filter);
    assertEquals(0, filterEngine.getListedFilters().size());
    assertFalse(filterEngine.getListedFilters().contains(filter));
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
    for (final TestFilterChangeCallback.Event event : callback.getEvents())
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
    filterEngine.removeFilterChangeCallback();
    callback.dispose();
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
    for (final TestFilterChangeCallback.Event event : callback.getEvents())
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
    filterEngine.removeFilterChangeCallback();
    callback.dispose();
  }

  @Test
  public void testAddRemoveSubscriptionsLegacy()
  {
    removeSubscriptions();

    assertEquals(0, filterEngine.getListedSubscriptions().size());
    final Subscription subscription = filterEngine.getSubscription("foo");
    assertEquals(0, filterEngine.getListedSubscriptions().size());
    assertFalse(filterEngine.getListedSubscriptions().contains(subscription));
    filterEngine.addSubscription(subscription);
    assertEquals(1, filterEngine.getListedSubscriptions().size());
    assertEquals(subscription, filterEngine.getListedSubscriptions().get(0));
    assertTrue(filterEngine.getListedSubscriptions().contains(subscription));
    filterEngine.addSubscription(subscription);
    assertEquals(1, filterEngine.getListedSubscriptions().size());
    assertEquals(subscription, filterEngine.getListedSubscriptions().get(0));
    assertTrue(filterEngine.getListedSubscriptions().contains(subscription));
    filterEngine.removeSubscription(subscription);
    assertEquals(0, filterEngine.getListedSubscriptions().size());
    assertFalse(filterEngine.getListedSubscriptions().contains(subscription));
    filterEngine.removeSubscription(subscription);
    assertEquals(0, filterEngine.getListedSubscriptions().size());
    assertFalse(filterEngine.getListedSubscriptions().contains(subscription));
  }

  @Test
  public void testAddRemoveSubscriptions()
  {
    removeSubscriptions();

    assertEquals(0, filterEngine.getListedSubscriptions().size());
    final Subscription subscription = filterEngine.getSubscription("foo");
    assertEquals(0, filterEngine.getListedSubscriptions().size());
    filterEngine.addSubscription(subscription);
    assertEquals(1, filterEngine.getListedSubscriptions().size());
    assertEquals(subscription, filterEngine.getListedSubscriptions().get(0));
    filterEngine.addSubscription(subscription);
    assertEquals(1, filterEngine.getListedSubscriptions().size());
    assertEquals(subscription, filterEngine.getListedSubscriptions().get(0));
    filterEngine.removeSubscription(subscription);
    assertEquals(0, filterEngine.getListedSubscriptions().size());
    filterEngine.removeSubscription(subscription);
    assertEquals(0, filterEngine.getListedSubscriptions().size());
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
    filterEngine.addFilter(filterEngine.getFilterFromText("adbanner.gif"));
    filterEngine.addFilter(filterEngine.getFilterFromText("@@notbanner.gif"));
    filterEngine.addFilter(filterEngine.getFilterFromText("tpbanner.gif$third-party"));
    filterEngine.addFilter(filterEngine.getFilterFromText("fpbanner.gif$~third-party"));
    filterEngine.addFilter(filterEngine.getFilterFromText("combanner.gif$domain=example.com"));
    filterEngine.addFilter(filterEngine.getFilterFromText("orgbanner.gif$domain=~example.com"));

    final Filter match1 = filterEngine.matches("http://example.org/foobar.gif",
        maskOf(ContentType.IMAGE), FilterEngine.EMPTY_PARENT, "", false);
    assertNull(match1);

    final Filter match2 = filterEngine.matches("http://example.org/adbanner.gif",
        maskOf(ContentType.IMAGE), FilterEngine.EMPTY_PARENT, "",false);
    assertNotNull(match2);
    assertEquals(Filter.Type.BLOCKING, match2.type);

    final Filter match5 = filterEngine.matches("http://example.org/tpbanner.gif",
        maskOf(ContentType.IMAGE), "http://example.org/", "", false);
    assertNull(match5);

    final Filter match6 = filterEngine.matches("http://example.org/fpbanner.gif",
        maskOf(ContentType.IMAGE), "http://example.org/", "", false);
    assertNotNull(match6);
    assertEquals(Filter.Type.BLOCKING, match6.type);

    final Filter match7 = filterEngine.matches("http://example.org/tpbanner.gif",
        maskOf(ContentType.IMAGE), "http://example.com/", "", false);
    assertNotNull(match7);
    assertEquals(Filter.Type.BLOCKING, match7.type);

    final Filter match8 = filterEngine.matches("http://example.org/fpbanner.gif",
        maskOf(ContentType.IMAGE), "http://example.com/", "", false);
    assertNull(match8);

    final Filter match9 = filterEngine.matches("http://example.org/combanner.gif",
        maskOf(ContentType.IMAGE), "http://example.com/", "", false);
    assertNotNull(match9);
    assertEquals(Filter.Type.BLOCKING, match9.type);

    final Filter match10 = filterEngine.matches("http://example.org/combanner.gif",
        maskOf(ContentType.IMAGE), "http://example.org/", "", false);
    assertNull(match10);

    final Filter match11 = filterEngine.matches("http://example.org/orgbanner.gif",
        maskOf(ContentType.IMAGE), "http://example.com/", "", false);
    assertNull(match11);

    final Filter match12 = filterEngine.matches("http://example.org/orgbanner.gif",
        maskOf(ContentType.IMAGE), "http://example.org/", "",false);
    assertNotNull(match12);
    assertEquals(Filter.Type.BLOCKING, match12.type);
  }

  @Test
  public void testGenericblock()
  {
    filterEngine.addFilter(
        filterEngine.getFilterFromText("/testcasefiles/genericblock/target-generic.jpg"));
    filterEngine.addFilter(
        filterEngine.getFilterFromText(
          "/testcasefiles/genericblock/target-notgeneric.jpg$domain=testpages.adblockplus.org"));

    final String urlGeneric = "http://testpages.adblockplus.org/testcasefiles/genericblock/target-generic.jpg";
    final String urlNotGeneric = "http://testpages.adblockplus.org/testcasefiles/genericblock/target-notgeneric.jpg";
    final String firstParent = "http://testpages.adblockplus.org/en/exceptions/genericblock/frame.html";

    final List<String> documentUrlsForGenericblock = new ArrayList<>();
    documentUrlsForGenericblock.add("http://testpages.adblockplus.org/testcasefiles/genericblock/frame.html");
    documentUrlsForGenericblock.add("http://testpages.adblockplus.org/en/exceptions/genericblock/");
    final String immediateParent = documentUrlsForGenericblock.get(0);

    // Go through all parent frames/refs to check if any of them has genericblock filter exception
    boolean specificOnly = filterEngine.isContentAllowlisted(firstParent,
        maskOf(ContentType.GENERICBLOCK), documentUrlsForGenericblock, "");
    assertFalse(specificOnly);
    Filter match1 = filterEngine.matches(urlNotGeneric, maskOf(ContentType.IMAGE),
        immediateParent, "", specificOnly);
    assertNotNull(match1);
    assertEquals(Filter.Type.BLOCKING, match1.type);

    specificOnly = filterEngine.isContentAllowlisted(firstParent,
        maskOf(ContentType.GENERICBLOCK), documentUrlsForGenericblock, "");
    assertFalse(specificOnly);
    Filter match2 = filterEngine.matches(urlGeneric, maskOf(ContentType.IMAGE),
        immediateParent, "", specificOnly);
    assertNotNull(match2);
    assertEquals(Filter.Type.BLOCKING, match2.type);

    // Now add genericblock filter and do the checks
    filterEngine.addFilter(
        filterEngine.getFilterFromText("@@||testpages.adblockplus.org/en/exceptions/genericblock$genericblock"));

    // Go again through all parent frames/refs to check if any of them has genericblock filter exception
    specificOnly = filterEngine.isContentAllowlisted(firstParent,
        maskOf(ContentType.GENERICBLOCK), documentUrlsForGenericblock, "");
    assertTrue(specificOnly);
    match1 = filterEngine.matches(urlNotGeneric, maskOf(ContentType.IMAGE),
        immediateParent, "", specificOnly);
    assertNotNull(match1);
    assertEquals(Filter.Type.BLOCKING, match1.type);

    specificOnly = filterEngine.isContentAllowlisted(firstParent,
        maskOf(ContentType.GENERICBLOCK), documentUrlsForGenericblock, "");
    assertTrue(specificOnly);
    match2 = filterEngine.matches(urlGeneric, maskOf(ContentType.IMAGE),
        immediateParent, "", specificOnly);
    assertNull(match2);
  }

  @Test
  public void testGenericblockHierarchy()
  {
    filterEngine.addFilter(
        filterEngine.getFilterFromText("@@||example.com^$genericblock,domain=example.com"));

    assertTrue(filterEngine.isContentAllowlisted(
        "http://example.com/add.png",
        maskOf(ContentType.GENERICBLOCK),
        Arrays.asList("http://example.com/frame.html", "http://example.com/index.html"),
        SITEKEY));
    assertFalse(filterEngine.isContentAllowlisted(
        "http://example.com/add.png",
        maskOf(ContentType.GENERICBLOCK),
        Arrays.asList("http://example.com/frame.html", "http://baddomain.com/index.html"),
        SITEKEY));
  }

  @Test
  public void testGenericblockWithDomain()
  {
    filterEngine.addFilter(
        filterEngine.getFilterFromText("@@||foo.example.com^$genericblock,domain=example.net"));
    filterEngine.addFilter(
        filterEngine.getFilterFromText("@@||bar.example.com^$genericblock,domain=~example.net"));

    assertTrue(filterEngine.isContentAllowlisted(
        "http://foo.example.com/ad.html",
        maskOf(ContentType.GENERICBLOCK),
        Arrays.asList("http://foo.example.com/", "http://example.net"), SITEKEY));
    assertFalse(filterEngine.isContentAllowlisted(
        "http://bar.example.com/ad.html",
        maskOf(ContentType.GENERICBLOCK),
        Arrays.asList("http://bar.example.com", "http://example.net"), SITEKEY));
  }

  @Test
  public void testGenerichide()
  {
    filterEngine.addFilter(
        filterEngine.getFilterFromText("##.testcase-generichide-generic"));
    filterEngine.addFilter(
        filterEngine.getFilterFromText("example.org##.testcase-generichide-notgeneric"));

    final String url = "http://www.example.org";

    // before generichide option
    assertFalse(filterEngine.isContentAllowlisted(url,
        ContentType.maskOf(ContentType.GENERICHIDE),
        Arrays.asList("http://www.example.org"), null));

    // add filter with generichide option
    filterEngine.addFilter(
        filterEngine.getFilterFromText("@@||example.org$generichide"));

    assertTrue(filterEngine.isContentAllowlisted(url,
        ContentType.maskOf(ContentType.GENERICHIDE),
        Arrays.asList("http://www.example.org"), null));
  }

  @Test
  public void testMatchesWithContentTypeMask()
  {
    filterEngine.addFilter(
        filterEngine.getFilterFromText("adbanner.gif.js$script,image"));
    filterEngine.addFilter(
        filterEngine.getFilterFromText("@@notbanner.gif"));
    filterEngine.addFilter(
        filterEngine.getFilterFromText("blockme"));
    final Filter domainAllowlistingFilter =
      filterEngine.getFilterFromText("@@||example.doc^$document,domain=example.doc");
    filterEngine.addFilter(domainAllowlistingFilter);
    filterEngine.addFilter(filterEngine.getFilterFromText("||popexample.com^$popup"));

    assertNull("another url should not match",
        filterEngine.matches("http://example.org/foobar.gif",
        maskOf(ContentType.IMAGE), FilterEngine.EMPTY_PARENT, "",
            false));

    assertNull("zero mask should not match (filter with some options)",
        filterEngine.matches("http://example.org/adbanner.gif.js",
        maskOf(), FilterEngine.EMPTY_PARENT,"", false));

    assertNull("zero mask should not match (filter without any option)",
        filterEngine.matches("http://example.xxx/blockme",
        maskOf(), FilterEngine.EMPTY_PARENT,"", false));

    assertNull("one arbitrary flag in mask should not match",
        filterEngine.matches("http://example.org/adbanner.gif.js",
            maskOf(ContentType.OBJECT), FilterEngine.EMPTY_PARENT, "",
            false));

    assertNotNull("one of flags in mask should match",
        filterEngine.matches("http://example.org/adbanner.gif.js",
            maskOf(
                ContentType.IMAGE,
                ContentType.OBJECT),
            FilterEngine.EMPTY_PARENT, "", false));

    assertNotNull("both flags in mask should match",
        filterEngine.matches("http://example.org/adbanner.gif.js",
            maskOf(
                ContentType.IMAGE,
                ContentType.SCRIPT),
            FilterEngine.EMPTY_PARENT,"", false));

    assertNotNull("both flags with another flag in mask should match",
        filterEngine.matches("http://example.org/adbanner.gif.js",
            maskOf(
                ContentType.IMAGE,
                ContentType.SCRIPT,
                ContentType.OBJECT),
            FilterEngine.EMPTY_PARENT,"", false));

    assertNotNull("one of flags in mask should match",
        filterEngine.matches("http://example.org/adbanner.gif.js",
            maskOf(
                ContentType.SCRIPT,
                ContentType.OBJECT),
            FilterEngine.EMPTY_PARENT,"", false));
  }

  @Test
  public void testSetRemoveFilterChangeCallback()
  {
    final TestFilterChangeCallback callback = new TestFilterChangeCallback();
    filterEngine.setFilterChangeCallback(callback);
    callback.getEvents().clear();
    filterEngine.addFilter(filterEngine.getFilterFromText("foo"));
    final int eventsCount = callback.getEvents().size();

    // we want to actually check the call count didn't change.
    filterEngine.removeFilterChangeCallback();
    filterEngine.removeFilter(filterEngine.getFilterFromText("foo"));
    assertEquals(eventsCount, callback.getEvents().size());
    filterEngine.removeFilterChangeCallback();
    callback.dispose();
  }

  @Test
  public void testDocumentAllowlisting()
  {
    filterEngine.addFilter(
        filterEngine.getFilterFromText("@@||example.org^$document"));
    filterEngine.addFilter(
        filterEngine.getFilterFromText("@@||example.com^$document,domain=example.de"));

    assertTrue(filterEngine.isContentAllowlisted("http://example.org/ad.html",
        maskOf(ContentType.DOCUMENT),
        Arrays.asList("http://example.org/ad.html"), FilterEngine.EMPTY_SITEKEY));
    assertFalse(filterEngine.isContentAllowlisted("http://example.co.uk/ad.html",
        maskOf(ContentType.DOCUMENT),
        Arrays.asList("http://example.co.uk/ad.html"), FilterEngine.EMPTY_SITEKEY));
    assertFalse(filterEngine.isContentAllowlisted("http://example.com/ad.html",
        maskOf(ContentType.DOCUMENT),
        Arrays.asList(FilterEngine.EMPTY_PARENT), FilterEngine.EMPTY_SITEKEY));
    assertTrue(filterEngine.isContentAllowlisted("http://example.com/ad.html",
        maskOf(ContentType.DOCUMENT),
        Arrays.asList("http://example.com", "http://example.de"), FilterEngine.EMPTY_SITEKEY));
    assertFalse(filterEngine.isContentAllowlisted("http://example.co.uk/ad.html",
        maskOf(ContentType.DOCUMENT),
        Arrays.asList("http://example.co.uk", "http://example.de"), FilterEngine.EMPTY_SITEKEY));
  }

  @Test
  public void testElemhideAllowlisting()
  {
    filterEngine.addFilter(
        filterEngine.getFilterFromText("@@||example.org^$elemhide"));
    filterEngine.addFilter(
        filterEngine.getFilterFromText("@@||example.com^$elemhide,domain=example.de"));

    assertTrue(filterEngine.isContentAllowlisted("http://example.org/file",
        maskOf(ContentType.ELEMHIDE), Arrays.asList("http://example.org"),
        FilterEngine.EMPTY_SITEKEY));
    assertFalse(filterEngine.isContentAllowlisted("http://example.co.uk/file",
        maskOf(ContentType.ELEMHIDE), Arrays.asList("http://example.co.uk"),
        FilterEngine.EMPTY_SITEKEY));
    assertFalse(filterEngine.isContentAllowlisted("http://example.com/file",
        maskOf(ContentType.ELEMHIDE), Arrays.asList("http://example.com"),
        FilterEngine.EMPTY_SITEKEY));
    assertTrue(filterEngine.isContentAllowlisted("http://example.com/ad.html",
        maskOf(ContentType.ELEMHIDE),
        Arrays.asList("http://example.com", "http://example.de"), FilterEngine.EMPTY_SITEKEY));
    assertFalse(filterEngine.isContentAllowlisted("http://example.co.uk/ad.html",
        maskOf(ContentType.ELEMHIDE),
        Arrays.asList("http://example.co.uk", "http://example.de"), FilterEngine.EMPTY_SITEKEY));
  }

  @Test
  public void testIsDocAndIsElemhideAllowlistedMatchesAllowlistedSiteKey()
  {
    filterEngine.addFilter(filterEngine.getFilterFromText("adframe"));
    final String docSiteKey = SITEKEY + "_document";
    final String elemhideSiteKey = SITEKEY + "_elemhide";
    filterEngine.addFilter(
        filterEngine.getFilterFromText("@@$document,sitekey=" + docSiteKey));
    filterEngine.addFilter(
        filterEngine.getFilterFromText("@@$elemhide,sitekey=" + elemhideSiteKey));

    // normally the frame is not allowlisted
    final String immediateParent = "http://example.com/";

    // no sitekey
    final Filter matchResult = filterEngine.matches(
        "http://my-ads.com/adframe",
        maskOf(ContentType.SUBDOCUMENT), immediateParent, FilterEngine.EMPTY_SITEKEY,
        false);
    assertNotNull(matchResult);
    assertEquals(Filter.Type.BLOCKING, matchResult.type);

    // random sitekey
    final Filter matchResult2 = filterEngine.matches(
        "http://my-ads.com/adframe",
        maskOf(ContentType.SUBDOCUMENT), immediateParent, SITEKEY, false);
    assertNotNull(matchResult2);
    assertEquals(Filter.Type.BLOCKING, matchResult2.type);

    // the frame itself
    final List<String> documentUrls2 = Arrays.asList(
        "http://example.com/",
        "http://ads.com/");

    // no sitekey
    assertFalse(filterEngine.isContentAllowlisted("http://my-ads.com/adframe",
        maskOf(ContentType.DOCUMENT), documentUrls2, FilterEngine.EMPTY_SITEKEY));
    assertFalse(filterEngine.isContentAllowlisted("http://my-ads.com/adframe",
        maskOf(ContentType.ELEMHIDE), documentUrls2, FilterEngine.EMPTY_SITEKEY));

    // random sitekey and the correct sitekey
    assertFalse(filterEngine.isContentAllowlisted("http://my-ads.com/adframe",
        maskOf(ContentType.DOCUMENT), documentUrls2, SITEKEY));
    assertTrue(filterEngine.isContentAllowlisted("http://my-ads.com/adframe",
        maskOf(ContentType.DOCUMENT), documentUrls2, docSiteKey));
    assertFalse(filterEngine.isContentAllowlisted("http://my-ads.com/adframe",
        maskOf(ContentType.ELEMHIDE), documentUrls2, SITEKEY));
    assertTrue(filterEngine.isContentAllowlisted("http://my-ads.com/adframe",
        maskOf(ContentType.ELEMHIDE), documentUrls2, elemhideSiteKey));

    // the frame within a allowlisted frame
    final List<String> documentUrls3 = Arrays.asList(
        "http://example.com/",
        "http:/my-ads.com/adframe",
        "http://ads.com/");

    // no sitekey
    assertFalse(filterEngine.isContentAllowlisted("http://some-ads.com",
        maskOf(ContentType.DOCUMENT), documentUrls3, FilterEngine.EMPTY_SITEKEY));
    assertFalse(filterEngine.isContentAllowlisted("http://some-ads.com",
        maskOf(ContentType.ELEMHIDE), documentUrls3, FilterEngine.EMPTY_SITEKEY));

    // random sitekey and the correct sitekey
    assertFalse(filterEngine.isContentAllowlisted("http://some-ads.com",
        maskOf(ContentType.DOCUMENT), documentUrls3, SITEKEY));
    assertTrue(filterEngine.isContentAllowlisted("http://some-ads.com",
        maskOf(ContentType.DOCUMENT), documentUrls3, docSiteKey));
    assertFalse(filterEngine.isContentAllowlisted("http://some-ads.com",
        maskOf(ContentType.ELEMHIDE), documentUrls3, SITEKEY));
    assertTrue(filterEngine.isContentAllowlisted("http://some-ads.com",
        maskOf(ContentType.ELEMHIDE), documentUrls3, elemhideSiteKey));
  }

  @Test
  public void testElementHidingStyleSheetEmpty()
  {
    final String sheet = filterEngine.getElementHidingStyleSheet("example.org");
    assertTrue(sheet.isEmpty());
  }

  @Test
  public void testElementHidingStyleSheet()
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
      filterEngine.addFilter(filterEngine.getFilterFromText(filter));
    }

    final String sheet = filterEngine.getElementHidingStyleSheet("example.org");

    assertNotNull(sheet);
    assertEquals("#testcase-eh-id {display: none !important;}\n" +
                "#testcase-eh-id, " +
                ".testcase-eh-class, " +
                ".testcase-container > .testcase-eh-descendant, " +
                "foo, " +
                "testneg {display: none !important;}\n",
            sheet);
  }

  @Test
  public void testElementHidingStyleSheetSingleGeneric()
  {
    // element hiding selectors
    filterEngine.addFilter(filterEngine.getFilterFromText("###testcase-eh-id"));

    final String sheet = filterEngine.getElementHidingStyleSheet("");

    assertNotNull(sheet);
    assertEquals("#testcase-eh-id {display: none !important;}\n", sheet);
  }

  @Test
  public void testElementHidingStyleSheetSingleDomain()
  {
    // element hiding selectors
    filterEngine.addFilter(filterEngine.getFilterFromText("example.org##.testcase - eh - class"));

    final String sheet = filterEngine.getElementHidingStyleSheet("example.org");

    assertNotNull(sheet);
    assertEquals(".testcase - eh - class {display: none !important;}\n", sheet);
  }

  @Test
  public void testElementHidingStyleSheetDup()
  {
    // element hiding selectors - duplicates
    filterEngine.addFilter(filterEngine.getFilterFromText("example.org###dup"));
    filterEngine.addFilter(filterEngine.getFilterFromText("example.org###dup"));
    filterEngine.addFilter(filterEngine.getFilterFromText("othersite.org###dup"));

    final String sheet = filterEngine.getElementHidingStyleSheet("example.org");

    // no dups
    assertNotNull(sheet);
    assertEquals("#dup {display: none !important;}\n", sheet);

    // this makes duplicates
    filterEngine.addFilter(filterEngine.getFilterFromText("~foo.example.org,example.org###dup"));
    filterEngine.addFilter(filterEngine.getFilterFromText("~bar.example.org,example.org###dup"));

    final String sheetDup = filterEngine.getElementHidingStyleSheet("example.org");

    // dups
    assertNotNull(sheetDup);
    assertEquals("#dup, #dup, #dup {display: none !important;}\n", sheetDup);

    final String sheetBar = filterEngine.getElementHidingStyleSheet("bar.example.org");
    assertNotNull(sheetBar);
    assertEquals("#dup, #dup {display: none !important;}\n", sheetBar);
  }

  @Test
  public void testElementHidingStyleSheetDiff()
  {
    filterEngine.addFilter(filterEngine.getFilterFromText("example1.org###testcase-eh-id"));
    filterEngine.addFilter(filterEngine.getFilterFromText("example2.org###testcase-eh-id"));

    final String sheet1 = filterEngine.getElementHidingStyleSheet("example1.org");
    assertEquals("#testcase-eh-id {display: none !important;}\n", sheet1);

    final String sheet2 = filterEngine.getElementHidingStyleSheet("example2.org");
    assertEquals("#testcase-eh-id {display: none !important;}\n", sheet2);

    final String sheet3 = filterEngine.getElementHidingStyleSheet("");
    assertTrue(sheet3.isEmpty());

    final String sheetNonExisting =
      filterEngine.getElementHidingStyleSheet("non-existing-domain.com");
    assertTrue(sheetNonExisting.isEmpty());
  }

  @Test
  public void testElementHidingStyleSheetGenerichide()
  {
    // element hiding styleSheet
    filterEngine.addFilter(
        filterEngine.getFilterFromText("##.testcase-generichide-generic"));
    filterEngine.addFilter(
        filterEngine.getFilterFromText("example.org##.testcase-generichide-notgeneric"));
    filterEngine.addFilter(
        filterEngine.getFilterFromText("@@||example.org$generichide"));

    final String styleSheet = filterEngine.getElementHidingStyleSheet("example.org");

    assertNotNull(styleSheet);
    assertEquals(".testcase-generichide-generic {display: none !important;}\n" +
            ".testcase-generichide-notgeneric {display: none !important;}\n", styleSheet);

    final String styleSheetSpecificOnly =
            filterEngine.getElementHidingStyleSheet("example.org", true);

    assertNotNull(styleSheetSpecificOnly);
    assertEquals(".testcase-generichide-notgeneric {display: none !important;}\n",
            styleSheetSpecificOnly);
  }

  @Test
  public void testElementHidingEmulationSelectorsListEmpty()
  {
    final List<EmulationSelector> selectors =
      filterEngine.getElementHidingEmulationSelectors("example.org");
    assertTrue(selectors.isEmpty());
  }

  @Test
  public void testElementHidingEmulationSelectorsAllowlist()
  {
    filterEngine.addFilter(filterEngine.getFilterFromText("example.org#?#foo"));

    // before allowlisting
    final List<EmulationSelector> selectorsBeforeAllowlisting =
      filterEngine.getElementHidingEmulationSelectors("example.org");
    assertEquals(1, selectorsBeforeAllowlisting.size());
    assertEquals("foo", selectorsBeforeAllowlisting.get(0).selector);
    assertEquals("example.org#?#foo", selectorsBeforeAllowlisting.get(0).text);

    // allowlist it
    filterEngine.addFilter(filterEngine.getFilterFromText("example.org#@#foo"));

    final List<EmulationSelector> selectorsAfterAllowlisting =
      filterEngine.getElementHidingEmulationSelectors("example.org");
    assertTrue(selectorsAfterAllowlisting.isEmpty());

    // add another filter
    filterEngine.addFilter(filterEngine.getFilterFromText("example.org#?#another"));

    final List<EmulationSelector> selectorsAnother =
      filterEngine.getElementHidingEmulationSelectors("example.org");
    assertEquals(1, selectorsAnother.size());
    assertEquals("another", selectorsAnother.get(0).selector);
    assertEquals("example.org#?#another", selectorsAnother.get(0).text);

    // check another domain
    filterEngine.addFilter(filterEngine.getFilterFromText("example2.org#?#foo"));

    final List<EmulationSelector> selectors2 =
      filterEngine.getElementHidingEmulationSelectors("example2.org");
    assertEquals(1, selectors2.size());
    assertEquals("foo", selectors2.get(0).selector);
    assertEquals("example2.org#?#foo", selectors2.get(0).text);

    // check the type of the allowlist (exception) filter
    final Filter filter = filterEngine.getFilterFromText("example.org#@#bar");
    assertEquals(Filter.Type.ELEMHIDE_EXCEPTION, filter.type);
  }

  @Test
  public void testElementHidingEmulationSelectorsList()
  {
    final String[] filters =
      {
        // other type of filters
        "/testcasefiles/blocking/addresspart/abptestcasepath/",
        "example.org###testcase-eh-id",

        // element hiding emulation selectors
        "example.org#?#div:-abp-properties(width: 213px)",
        "example.org#?#div:-abp-has(>div>img.testcase-es-has)",
        "example.org#?#span:-abp-contains(ESContainsTarget)",
        "~foo.example.org,example.org#?#div:-abp-properties(width: 213px)",
        "~othersiteneg.org#?#div:-abp-properties(width: 213px)",

        // allowlisted
        "example.org#@#foo",

        // other site
        "othersite.com###testcase-eh-id"
      };

    for (final String filter : filters)
    {
      filterEngine.addFilter(filterEngine.getFilterFromText(filter));
    }

    final List<EmulationSelector> selectors =
      filterEngine.getElementHidingEmulationSelectors("example.org");

    assertEquals(4, selectors.size());
    assertEquals("div:-abp-properties(width: 213px)", selectors.get(0).selector);
    assertEquals("div:-abp-has(>div>img.testcase-es-has)", selectors.get(1).selector);
    assertEquals("span:-abp-contains(ESContainsTarget)", selectors.get(2).selector);
    assertEquals("div:-abp-properties(width: 213px)", selectors.get(3).selector);

    // text field
    assertEquals("example.org#?#div:-abp-properties(width: 213px)", selectors.get(0).text);
    assertEquals("example.org#?#div:-abp-has(>div>img.testcase-es-has)", selectors.get(1).text);
    assertEquals("example.org#?#span:-abp-contains(ESContainsTarget)", selectors.get(2).text);
    assertEquals("~foo.example.org,example.org#?#div:-abp-properties(width: 213px)", selectors.get(3).text);

    final List<EmulationSelector> selectors2 =
      filterEngine.getElementHidingEmulationSelectors("foo.example.org");
    assertEquals(3, selectors2.size());
    assertEquals("div:-abp-properties(width: 213px)", selectors2.get(0).selector);
    assertEquals("div:-abp-has(>div>img.testcase-es-has)", selectors2.get(1).selector);
    assertEquals("span:-abp-contains(ESContainsTarget)", selectors2.get(2).selector);

    assertEquals("example.org#?#div:-abp-properties(width: 213px)", selectors2.get(0).text);
    assertEquals("example.org#?#div:-abp-has(>div>img.testcase-es-has)", selectors2.get(1).text);
    assertEquals("example.org#?#span:-abp-contains(ESContainsTarget)", selectors2.get(2).text);

    final List<EmulationSelector> selectors3 =
      filterEngine.getElementHidingEmulationSelectors("othersiteneg.org");
    assertEquals(0, selectors3.size());
  }

  @Test
  public void testElementHidingEmulationSelectorsListSingleDomain()
  {
    // element hiding emulation selector
    filterEngine.addFilter(filterEngine.getFilterFromText("example.org#?#div:-abp-properties(width: 213px)"));

    final List<EmulationSelector> selectors =
      filterEngine.getElementHidingEmulationSelectors("example.org");

    assertEquals(1, selectors.size());
    assertEquals("div:-abp-properties(width: 213px)", selectors.get(0).selector);
    assertEquals("example.org#?#div:-abp-properties(width: 213px)", selectors.get(0).text);
  }

  @Test
  public void testElementHidingEmulationSelectorsListNoDuplicates()
  {
    // element hiding emulation selectors - duplicates
    filterEngine.addFilter(filterEngine.getFilterFromText("example.org#?#dup"));
    filterEngine.addFilter(filterEngine.getFilterFromText("example.org#?#dup"));
    filterEngine.addFilter(filterEngine.getFilterFromText("othersite.org#?#dup"));
    filterEngine.addFilter(filterEngine.getFilterFromText("~foo.example.org#?#dup"));

    final List<EmulationSelector> selectors =
      filterEngine.getElementHidingEmulationSelectors("example.org");

    // no dups
    assertEquals(1, selectors.size());
    assertEquals("dup", selectors.get(0).selector);
    assertEquals("example.org#?#dup", selectors.get(0).text);
  }

  @Test
  public void testElementHidingEmulationSelectorsListDuplicates()
  {
    // element hiding emulation selectors - duplicates
    filterEngine.addFilter(filterEngine.getFilterFromText("example.org#?#dup"));
    filterEngine.addFilter(filterEngine.getFilterFromText("~foo.example.org,example.org#?#dup"));
    filterEngine.addFilter(filterEngine.getFilterFromText("~bar.example.org,example.org#?#dup"));

    final List<EmulationSelector> selectorsDup =
      filterEngine.getElementHidingEmulationSelectors("example.org");

    // dups
    assertEquals(3, selectorsDup.size());
    assertEquals("dup", selectorsDup.get(0).selector);
    assertEquals("dup", selectorsDup.get(1).selector);
    assertEquals("dup", selectorsDup.get(2).selector);

    assertEquals("example.org#?#dup", selectorsDup.get(0).text);
    assertEquals("~foo.example.org,example.org#?#dup", selectorsDup.get(1).text);
    assertEquals("~bar.example.org,example.org#?#dup", selectorsDup.get(2).text);

    final List<EmulationSelector> selectorsBar =
      filterEngine.getElementHidingEmulationSelectors("bar.example.org");
    assertEquals(2, selectorsBar.size());
    assertEquals("dup", selectorsBar.get(0).selector);
    assertEquals("dup", selectorsBar.get(1).selector);

    assertEquals("example.org#?#dup", selectorsBar.get(0).text);
    assertEquals("~foo.example.org,example.org#?#dup", selectorsBar.get(1).text);
  }

  @Test
  public void testElementHidingEmulationSelectorsListDiff()
  {
    filterEngine.addFilter(filterEngine.getFilterFromText("example1.org#?#div:-abp-properties(width: 213px)"));
    filterEngine.addFilter(filterEngine.getFilterFromText("example2.org#?#div:-abp-properties(width: 213px)"));
    // allowlisted
    filterEngine.addFilter(filterEngine.getFilterFromText("example2.org#@#div:-abp-properties(width: 213px)"));

    final List<EmulationSelector> selectors1 =
      filterEngine.getElementHidingEmulationSelectors("example1.org");
    assertEquals(1, selectors1.size());
    assertEquals("div:-abp-properties(width: 213px)", selectors1.get(0).selector);
    assertEquals("example1.org#?#div:-abp-properties(width: 213px)", selectors1.get(0).text);

    final List<EmulationSelector> selectors2 =
      filterEngine.getElementHidingEmulationSelectors("example2.org");
    assertTrue(selectors2.isEmpty());
  }

  @Test
  public void testElementHidingEmulationSelectorsGeneric()
  {
    filterEngine.addFilter(filterEngine.getFilterFromText("example1.org#?#foo"));
    filterEngine.addFilter(filterEngine.getFilterFromText("example2.org#@#bar"));

    // there are no generic el-hiding emulation filters.
    // this should have no effect on selectors returned and the type should be invalid
    final Filter genFilter = filterEngine.getFilterFromText("#?#foo");
    filterEngine.addFilter(genFilter);

    assertEquals(Filter.Type.INVALID, genFilter.type);

    final List<EmulationSelector> selectorsGen =
      filterEngine.getElementHidingEmulationSelectors("");
    assertTrue(selectorsGen.isEmpty());
  }

  @Test
  public void testElementHidingEmulationSelectorsNonExisting()
  {
    filterEngine.addFilter(filterEngine.getFilterFromText("example1.org#?#foo"));
    filterEngine.addFilter(filterEngine.getFilterFromText("example2.org#@#bar"));

    final List<EmulationSelector> selectorsNonExisting =
      filterEngine.getElementHidingEmulationSelectors("non-existing-domain.com");
    assertTrue(selectorsNonExisting.isEmpty());
  }

  @Test
  public void testIssueDp145()
  {
    filterEngine.addFilter(filterEngine.getFilterFromText("||aaxdetect.com/pxext.gif$domain=aaxdetect.com"));

    final String url = "https://aaxdetect.com/pxext.gif?&type=2&vn=1";

    // WARNING: order of referrers is important! Reverse order will return `null` as filter match
    final String immediateParent =
       "https://aaxdetect.com/detect.html?&pub=AAXSFY9XU&svr=2019040811_2780&gdpr=0&gdprconsent=0&dn=http%3A%2F%2Faaxdemo.com";

    final Filter matchResult = filterEngine.matches(
        url, maskOf(ContentType.IMAGE), immediateParent, FilterEngine.EMPTY_SITEKEY,
        false);
    assertNotNull(matchResult);
    assertEquals(Filter.Type.BLOCKING, matchResult.type);
  }
}
