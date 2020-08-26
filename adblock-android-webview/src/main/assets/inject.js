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

console.log("injected JS started")
var tryReceiveSiteKey = function()
{
  // A bug in Android 6, when navigation starts from 'about:blank', which has empty html tag.
  // But the document object is inherited to the further navigation, so we are getting in a state
  // when `onSiteKeyDoesNotExist` has been already called and no further checks are performed
  if (!window.location.href.startsWith('http')) return;

  // SITEKEY_EXTRACTED_FLAG acts for a flag that we have received sitekey or could not do it
  if (!document.{{SITEKEY_EXTRACTED_FLAG}})
  {
    {{DEBUG}}console.log('Getting sitekey on '+ window.location.href);
    // checking if html tag is ready
    let htmlCollection = document.getElementsByTagName('html');
    let htmlTag = htmlCollection ? htmlCollection[0] : null;
    if (htmlTag)
    {
      let key = (htmlTag.attributes['data-adblockkey'] || {}).value;
      // it might return "null" as a string, cleaning up
      if (key === "null")
      {
        key = null;
      }
      if (key)
      {
        {{DEBUG}}console.log("Sitekey received");
        AbpCallback.onSiteKeyExtracted(key, window.location.href, navigator.userAgent);
      }
      else
      {
        {{DEBUG}}console.log("Sitekey does not exist in html tag");
        AbpCallback.onSiteKeyDoesNotExist(window.location.href);
      }
      // Let's remember we have received the sitekey
      document.{{SITEKEY_EXTRACTED_FLAG}} = true;
    }
    else // if html tag is not ready, sending `onDomNotReady` event
    {
      // here we tend to hold the lock while the dom is not ready
      // to receive the key. However, this is likely to be seldom
      AbpCallback.onDomNotReady(window.location.href);
    }
  }
}

var hideElements = function()
{
  // no need to invoke if already invoked on another event
  if (document.{{HIDDEN_FLAG}} === true)
  {
    {{DEBUG}} console.log('already hidden, exiting');
    return;
  }

  {{DEBUG}} console.log("Not yet hidden!")

  // hide by injecting CSS stylesheet
  {{HIDE}}

  document.{{HIDDEN_FLAG}} = true; // set flag not to do it again
};

if (document.readyState === "complete")
{
  {{DEBUG}} console.log('document is in "complete" state, apply hiding')
  hideElements();
}
else
{
  {{DEBUG}} console.log('installing listener')

  // onreadystatechange event
  document.onreadystatechange = function()
  {
    {{DEBUG}} console.log('onreadystatechange() event fired (' + document.readyState + ')')
    if (document.readyState == 'interactive')
    {
      hideElements();
    }
  }

   // load event
  window.addEventListener('load', function(event)
  {
    {{DEBUG}} console.log('load() event fired');
    hideElements();
  });

  // DOMContentLoaded event
  document.addEventListener('DOMContentLoaded', function()
  {
    {{DEBUG}} console.log('DOMContentLoaded() event fired');
    hideElements();
  }, false);
}

// the formula = i * i * 8
const SITEKEY_RETRIEVAL_ATTEMPT_INTERVALS = [0, (1 * 8), (4 * 8), (9 * 8), (16 * 8)];

// we try to retrieve the sitekey right away (~20% success) and schedule retrieving sitekey
// with the following intervals (ms): 0, 8, 32, 72, 128
tryReceiveSiteKey();
// we are using "for loop" to make it more simple and reliable
for (let i = 0; i < SITEKEY_RETRIEVAL_ATTEMPT_INTERVALS.length; i++)
{
  setTimeout(function()
  {
    tryReceiveSiteKey();
  },(SITEKEY_RETRIEVAL_ATTEMPT_INTERVALS[i]));
}

console.log("injected JS finished");
