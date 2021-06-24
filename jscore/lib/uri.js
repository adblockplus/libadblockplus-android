/* !
 * Parts of original code from ipv6.js <https://github.com/beaugunderson/javascript-ipv6>
 * Copyright 2011 Beau Gunderson
 * Available under MIT license <http://mths.be/mit>
 */

const punycode = require("punycode");

/**
 * Extracts host name from a URL.
 *
 * @param {string} url to extract
 * @returns {string} extracted host
 */
function extractHostFromURL(/** String*/ url)
{
  if (url && extractHostFromURL._lastURL == url)
    return extractHostFromURL._lastDomain;

  let host = "";
  try
  {
    host = new URI(url).host;
  }
  catch (e)
  {
    // Keep the empty string for invalid URIs.
  }

  extractHostFromURL._lastURL = url;
  extractHostFromURL._lastDomain = host;
  return host;
}

/**
 * Parses URLs and provides an interface similar to nsIURI in Gecko, see
 * https://developer.mozilla.org/en-US/docs/XPCOM_Interface_Reference/nsIURI.
 * TODO: Make sure the parsing actually works the same as nsStandardURL.
 * @param {string} spec
 * @constructor
 */
function URI(/** String*/ spec)
{
  this.spec = spec;
  this._schemeEnd = spec.indexOf(":");
  if (this._schemeEnd < 0)
    throw new Error("Invalid URI scheme");

  if (spec.substr(this._schemeEnd + 1, 2) != "//")
    throw new Error("Unexpected URI structure");

  this._hostPortStart = this._schemeEnd + 3;
  if (this._hostPortStart == spec.length)
    throw new Error("Empty URI host");

  this._hostPortEnd = spec.indexOf("/", this._hostPortStart);
  if (this._hostPortEnd < 0)
  {
    let queryIndex = spec.indexOf("?", this._hostPortStart);
    let fragmentIndex = spec.indexOf("#", this._hostPortStart);
    if (queryIndex >= 0 && fragmentIndex >= 0)
      this._hostPortEnd = Math.min(queryIndex, fragmentIndex);
    else if (queryIndex >= 0)
      this._hostPortEnd = queryIndex;
    else if (fragmentIndex >= 0)
      this._hostPortEnd = fragmentIndex;
    else
      this._hostPortEnd = spec.length;
  }

  let authEnd = spec.indexOf("@", this._hostPortStart);
  if (authEnd >= 0 && authEnd < this._hostPortEnd)
    this._hostPortStart = authEnd + 1;

  this._portStart = -1;
  this._hostEnd = spec.indexOf("]", this._hostPortStart + 1);
  if (spec[this._hostPortStart] ==
    "[" && this._hostEnd >= 0 && this._hostEnd < this._hostPortEnd)
  {
    // The host is an IPv6 literal
    this._hostStart = this._hostPortStart + 1;
    if (spec[this._hostEnd + 1] == ":")
      this._portStart = this._hostEnd + 2;
  }
  else
  {
    this._hostStart = this._hostPortStart;
    this._hostEnd = spec.indexOf(":", this._hostStart);
    if (this._hostEnd >= 0 && this._hostEnd < this._hostPortEnd)
      this._portStart = this._hostEnd + 1;
    else
      this._hostEnd = this._hostPortEnd;
  }
}
URI.prototype =
{
  spec: null,
  get scheme()
  {
    return this.spec.substring(0, this._schemeEnd).toLowerCase();
  },
  get host()
  {
    return this.spec.substring(this._hostStart, this._hostEnd);
  },
  get asciiHost()
  {
    let host = this.host;
    if (/^[\x00-\x7F]+$/.test(host))
      return host;
    return punycode.toASCII(host);
  },
  get hostPort()
  {
    return this.spec.substring(this._hostPortStart, this._hostPortEnd);
  },
  get port()
  {
    if (this._portStart < 0)
      return -1;
    return parseInt(
      this.spec.substring(this._portStart, this._hostPortEnd), 10);
  },
  get path()
  {
    return this.spec.substring(this._hostPortEnd);
  },
  get prePath()
  {
    return this.spec.substring(0, this._hostPortEnd);
  }
};

export {URI, extractHostFromURL};
