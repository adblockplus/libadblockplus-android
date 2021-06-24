/* eslint-disable no-unused-vars */
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

export let addonName = "libadblockplus-android";
export let addonVersion = "1.0";
export let application = "unknown";
export let applicationVersion = "0";
export let platform;
export let platformVersion = "0";

function parseChromiumUserAgent()
{
  // we'd like to reuse this in future implementation
  let regexp = /(\S+)\/(\S+)(?:\s*\(.*?\))?/g;
  let match;

  /*
  Implement this

  An example from webextsdk:

  while (match = regexp.exec(navigator.userAgent))
  {
    let [, app, version] = match;

    // For compatibility with legacy websites, Chrome's UA
    // also includes a Mozilla, AppleWebKit and Safari tokens.
    // Any further name/version pair indicates a fork.
    if (app == "Mozilla" || app == "AppleWebKit" || app == "Safari")
      continue;

    if (app == "Chrome")
    {
      platformVersion = version;
      if (application != "unknown")
        continue;
    }

    application = app == "OPR" ? "opera" : app.toLowerCase();
    applicationVersion = version;
  }
  */
}

if (typeof netscape != "undefined")
{
  platform = "gecko";

  /*
  Implement this

  An example from webextsdk:

  let match = /\brv:([^;)]+)/.exec(navigator.userAgent);
  if (match)
    platformVersion = match[1];

  browser.runtime.getBrowserInfo().then(browserInfo =>
  {
    application = browserInfo.name.toLowerCase();
    applicationVersion = browserInfo.version;
  });
  */
}
else
{
  platform = "chromium";
  parseChromiumUserAgent();
}
