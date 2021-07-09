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
const {Logging} = require("./log");
const {Filter} = require("filterClasses");
const {defaultMatcher} = require("matcher");
const {elemHide} = require("elemHide");
const {elemHideEmulation} = require("elemHideEmulation");
const {elemHideExceptions} = require("elemHideExceptions")
// We don't support snippets but we need to store those filters to compare correctly memory usage
const {snippets} = require("snippets")
const {parseURL} = require("url");
const {URI, extractHostFromURL} = require('./uri')
const {startEngine} = require("./init");

function getURLInfo(url)
{
  // Parse the minimum URL to get a URLInfo instance.
  let urlInfo = parseURL("http://a.com/");

  try
  {
    let uri = new URI(url);

    // Note: There is currently no way to update the URLInfo object other
    // than to set the private properties directly.
    urlInfo._href = url;
    urlInfo._protocol = uri.scheme + ":";
    urlInfo._hostname = uri.asciiHost;
  }
  catch (error)
  {
    urlInfo = null;
  }

  return urlInfo;
}

export function checkFilterMatch(url, contentTypeMask, documentUrl, siteKey, specificOnly)
{
  let urlInfo = getURLInfo(url);
  if (!urlInfo)
  {
    return null;
  }

  let documentHost = extractHostFromURL(documentUrl);

  // Number cast to 32-bit integer then back to Number before passing to the API
  // @see https://stackoverflow.com/a/11385688
  //
  // During the typecast (upcast) in JsEngine#newValue(int) int -> int64_t (signed long)
  // sign bit is transfered to the new value hence the value becomes negative
  // (0x80000000 -> -0x80000000 = 0x80000000 -> 0xffffffff80000000)
  // we have to convert it to unsigned before passing futher
  contentTypeMask >>>= 0;

  let filter = defaultMatcher.match(urlInfo, contentTypeMask, documentHost, siteKey, specificOnly);
  return filter == null ? null : filter.type;
}

export function getElementHidingStyleSheet(domain, specificOnly)
{
  return elemHide.getStyleSheet(domain, specificOnly).code;
}

export function getElementHidingEmulationSelectors(domain)
{
  return elemHideEmulation.getFilters(domain);
}

function selectModule(type)
{
  switch (type)
  {
    case "blocking":
    case "allowing":
      return defaultMatcher;
    case "elemhide":
      return elemHide;
    case "elemhideemulation":
      return elemHideEmulation;
    case "elemhideexception":
      return elemHideExceptions;
    case "snippet":
      return snippets;
  }

  return null;
}

export function addFilter(filterText)
{
  let filter = Filter.fromText(filterText);
  let module = selectModule(filter.type);
  if (module)
    module.add(filter);
}

export function removeFilter(filterText)
{
  let filter = Filter.fromText(filterText);
  let module = selectModule(filter.type);
  if (module)
    module.remove(filter);
}

export function clearFilters()
{
  defaultMatcher.clear();
  elemHide.clear();
  elemHideEmulation.clear();
  elemHideExceptions.clear();
  snippets.clear();
}
