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

"use strict";

//
// Fake XMLHttpRequest implementation
//

function XMLHttpRequest()
{
  this._requestHeaders = {};
  this._loadHandlers = [];
  this._errorHandlers = [];
}
XMLHttpRequest.prototype =
{
  _method: null,
  _url: null,
  _requestHeaders: null,
  _responseHeaders: null,
  _loadHandlers: null,
  _errorHandlers: null,
  onload: null,
  onerror: null,
  status: 0,
  readyState: 0,
  responseText: null,

  // list taken from https://developer.mozilla.org/en-US/docs/Glossary/Forbidden_header_name
  _forbiddenRequestHeaders: new Set([
    "accept-charset",
    "accept-encoding",
    "access-control-request-headers",
    "access-control-request-method",
    "connection",
    "content-length",
    "cookie",
    "cookie2",
    "date",
    "dnt",
    "expect",
    "host",
    "keep-alive",
    "origin",
    "referer",
    "te",
    "trailer",
    "transfer-encoding",
    "upgrade",
    "via"
  ]),
  _forbiddenRequestHeadersRe: new RegExp("^(Proxy|Sec)-", "i"),

  _isRequestHeaderAllowed(header)
  {
    if (this._forbiddenRequestHeaders.has(header.toLowerCase()))
      return false;
    if (header.match(this._forbiddenRequestHeadersRe))
      return false;

    return true;
  },

  _doWebRequest(method, url, requestHeaders, onRequestDone)
  {
    // fake web request
  },

  addEventListener(eventName, handler, capture)
  {
    let list;
    if (eventName == "load")
      list = this._loadHandlers;
    else if (eventName == "error")
      list = this._errorHandlers;
    else
      throw new Error("Event type " + eventName + " not supported");

    if (list.indexOf(handler) < 0)
      list.push(handler);
  },

  removeEventListener(eventName, handler, capture)
  {
    let list;
    if (eventName == "load")
      list = this._loadHandlers;
    else if (eventName == "error")
      list = this._errorHandlers;
    else
      throw new Error("Event type " + eventName + " not supported");

    let index = list.indexOf(handler);
    if (index >= 0)
      list.splice(index, 1);
  },

  open(method, url, async, user, password)
  {
    if (method != "GET" && method != "HEAD")
      throw new Error("Only GET and HEAD requests are currently supported");
    if (typeof async != "undefined" && !async)
      throw new Error("Sync requests are not supported");
    if (typeof user != "undefined" || typeof password != "undefined")
      throw new Error("User authentication is not supported");
    if (this.readyState != 0)
      throw new Error("Already opened");

    this.readyState = 1;
    this._url = url;
    this._method = method;
  },

  _fail(onRequestDone)
  {
    onRequestDone({
      status: 0x804b000d, // NS_ERROR_CONNECTION_REFUSED;
      responseStatus: 0
    });
  },

  send(data)
  {
    if (this.readyState != 1)
      throw new Error(
        "XMLHttpRequest.send() is being called before XMLHttpRequest.open()");
    if (typeof data != "undefined" && data)
      throw new Error("Sending data to server is not supported");

    this.readyState = 3;

    let onRequestDone = result =>
    {
      this.status = result.responseStatus;
      this.responseText = result.responseText;
      this._responseHeaders = result.responseHeaders;
      this.readyState = 4;

      // Notify event listeners
      const NS_OK = 0;
      let eventName = (result.status == NS_OK ? "load" : "error");
      let event = {type: eventName};

      if (this["on" + eventName])
        this["on" + eventName].call(this, event);

      let list = this["_" + eventName + "Handlers"];
      for (let i = 0; i < list.length; i++)
        list[i].call(this, event);
    };

    // #1319: Now IFilterEngine and Updater are separated, so we want to
    // allow update requests no matter if subscriptions download requests
    // are allowed or not.
    if (this._url.includes("update.json"))
    {
      this._doWebRequest(this._method, this._url, this._requestHeaders, onRequestDone);
      return;
    }

    if (!require("prefs").Prefs.filter_engine_enabled)
    {
      this._fail(onRequestDone);
      return;
    }

    // HACK (#5066): the code checking whether the connection is
    // allowed is temporary, the actual check should be in the core
    // when we make a decision whether to update a subscription with
    // current connection or not, thus whether to even construct
    // XMLHttpRequest responseect or not.
    _isSubscriptionDownloadAllowed(isAllowed =>
    {
      if (!isAllowed)
      {
        this._fail(onRequestDone);
        return;
      }
      this._doWebRequest(this._method, this._url, this._requestHeaders, onRequestDone);
    });
  },

  overrideMimeType(mime)
  {
  },

  setRequestHeader(name, value)
  {
    if (this.readyState > 1)
      throw new Error("Cannot set request header after sending");

    if (this._isRequestHeaderAllowed(name))
      this._requestHeaders[name] = value;
    else
      console.warn("Attempt to set a forbidden header was denied: " + name);
  },

  getResponseHeader(name)
  {
    name = name.toLowerCase();
    if (!this._responseHeaders || !this._responseHeaders.hasOwnProperty(name))
      return null;
    return this._responseHeaders[name];
  }
};

//
// Fake URL implementation
//

class URL
{
  constructor(url, baseUrl)
  {
    // This is used by the Downloader class in adblockpluscore only to validate
    // the URL.
    // https://gitlab.com/eyeo/adblockplus/adblockpluscore/-/blob/eaae9e06f92d25b1d01bbeb19d3faf4019ef4ebc/lib/downloader.js#L251
    this.href = url;

    if (baseUrl && !url.includes(':'))
    {
      // Remove trailing slashes, to avoid something like base///realtive
      this.href = baseUrl.replace(/\/+$/, '') + '/' + url.replace(/^\/+/, '');
    }

    // Note: Unlike the real URL constructor, this may throw an error if the
    // URL is not normalized. This implementation is used for the URLs of
    // filter list subscriptions and notification feeds only. They are expected
    // to be normalized. If an error is thrown here, the Downloader class will
    // treat the URL as an invalid URL.
    //
    // The regular expression below comes from parseURL() in adblockpluscore
    // https://issues.adblockplus.org/ticket/7296
    let [, protocol, hostname] =
      /^([^:]+:)(?:\/\/(?:[^/]*@)?(\[[^\]]*\]|[^:/]+))?/.exec(this.href);

    this.protocol = protocol;
    this.hostname = hostname;
  }
}

//
// Fake fetch() implementation
//

function fetch(requestUrl, initObj)
{
    var response =
    {
      type: "basic",
      url: requestUrl,
      redirected: false,
      status: 404,
      ok: false,
      statusText: "",
      __proto__: Response
    };
    return response;
}

function _isSubscriptionDownloadAllowed(callback)
{
  // It's a bit hacky, JsEngine interface which is used by IFilterEngine does
  // not allow to inject an arbitrary callback, so we use triggerEvent
  // mechanism.
  // Yet one hack (#5039).
  let allowedConnectionType = require("prefs").Prefs.allowed_connection_type;
  if (allowedConnectionType == "")
    allowedConnectionType = null;
  _triggerEvent("_isSubscriptionDownloadAllowed", allowedConnectionType,
                callback);
}

/**
 * Fix for jsbn.js
 */
const navigator = {
  appName: ""
};
