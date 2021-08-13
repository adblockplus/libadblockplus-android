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

#include "Api.h"
#include "hermes/hermes.h"

using namespace facebook::jsi;

const char JAVASCRIPT_OBJECT[] = "API";
const char JAVASCRIPT_FUNCTION_GET_EH_SS[] = "getElementHidingStyleSheet";
const char JAVASCRIPT_FUNCTION_GET_EH_ES[] = "getElementHidingEmulationSelectors";
const char JAVASCRIPT_FUNCTION_MATCHES[] = "checkFilterMatch";

const char JAVASCRIPT_FUNCTION_FILTER_ADD[] = "addFilter";
const char JAVASCRIPT_FUNCTION_FILTER_REMOVE[] = "removeFilter";

const char JAVASCRIPT_FUNCTION_SUBSC_IS_LISTED[] = "isListedSubscription";
const char JAVASCRIPT_FUNCTION_SUBSC_ADD[] = "addSubscriptionToList";
const char JAVASCRIPT_FUNCTION_SUBSC_REMOVE[] = "removeSubscriptionFromList";
const char JAVASCRIPT_FUNCTION_SUBSC_LIST[] = "getListedSubscriptions";
const char JAVASCRIPT_FUNCTION_SUBSC_GET[] = "getSubscriptionByUrl";
const char JAVASCRIPT_FUNCTION_SUBSC_SET_AA_ENABLED[] = "setAASubscriptionEnabled";

const char JAVA_CLASS_EMULATION_SELECTOR[] = "org/adblockplus/EmulationSelector";
const char JAVA_CLASS_SUBSCRIPTION[] = "org/adblockplus/Subscription";


jboolean Api::isContentAllowlisted(hermesptr runtime, jint contentTypeMask,
                                   alias_ref<JList<JString> > referrerChain,
                                   alias_ref<JString> siteKey)
{
  const auto jsSiteKey = String::createFromAscii(*runtime, siteKey->toStdString());
  const auto jsContentTypeMask = Value(contentTypeMask);
  const auto nativeMatches = getFunction(runtime, JAVASCRIPT_FUNCTION_MATCHES);

  for (auto it = referrerChain->begin(); it != referrerChain->end();)
  {
    const auto currentUrl = String::createFromAscii(*runtime, it.entry_->toStdString());
    ++it;
    const auto parentUrl = (it != referrerChain->end()) ?
                           String::createFromAscii(*runtime, it.entry_->toStdString()) :
                           String::createFromAscii(*runtime, "");
    Value value;
    if (parentUrl.utf8(*runtime).empty())
    {
      value = nativeMatches.call(*runtime, currentUrl, jsContentTypeMask, currentUrl, jsSiteKey, false);
    }
    else
    {
      value = nativeMatches.call(*runtime, currentUrl, jsContentTypeMask, parentUrl, jsSiteKey, false);
    }
    if (value.isString())
    {
      return JNI_TRUE;
    }
  }
  return JNI_FALSE;
}

std::string Api::matches(hermesptr runtime, alias_ref<JString> url, jint contentTypeMask,
                         alias_ref<JString> parent, alias_ref<JString> siteKey, jboolean specificOnly)
{
  const auto jsUrl = String::createFromAscii(*runtime, url->toStdString());
  const auto jsContentTypeMask = Value(contentTypeMask);
  const auto jsParent = String::createFromAscii(*runtime, parent->toStdString());
  const auto jsSiteKey = String::createFromAscii(*runtime, siteKey->toStdString());
  const auto jsSpecificOnly = Value(specificOnly != 0);
  const auto value = getFunction(runtime, JAVASCRIPT_FUNCTION_MATCHES)
      .call(*runtime, jsUrl, jsContentTypeMask, jsParent, jsSiteKey, jsSpecificOnly);
  return value.isString() ? value.asString(*runtime).utf8(*runtime) : "";
}

std::string Api::getElementHidingStyleSheet(hermesptr runtime, alias_ref<JString> domain, jboolean specificOnly)
{
  const auto jsDomain = String::createFromAscii(*runtime, domain->toStdString());
  const auto jsSpecificOnly = Value(specificOnly != 0);
  const auto value = getFunction(runtime, JAVASCRIPT_FUNCTION_GET_EH_SS)
      .call(*runtime, jsDomain, jsSpecificOnly);
  return value.isString() ? value.asString(*runtime).utf8(*runtime) : "";
}

JObjectArrayLocalRef Api::getElementHidingEmulationSelectors(hermesptr runtime, alias_ref<JString> domain)
{
  const auto jsDomain = String::createFromAscii(*runtime, domain->toStdString());
  const auto array = getFunction(runtime, JAVASCRIPT_FUNCTION_GET_EH_ES)
      .call(*runtime, jsDomain)
      .asObject(*runtime).asArray(*runtime);
  const auto arraySize = array.length(*runtime);

  const auto emuSelectorClass = findClassLocal(JAVA_CLASS_EMULATION_SELECTOR);
  JObjectArrayLocalRef result = JArrayClass<jobject>::newArray(arraySize);

  if (arraySize > 0)
  {
    const auto emuSelectorConstructor = emuSelectorClass->getConstructor<jobject(jstring, jstring)>();

    for (int i = 0; i < arraySize; ++i)
    {
      const auto obj = array.getValueAtIndex(*runtime, i).asObject(*runtime);
      auto selectorStr = make_jstring(obj.getProperty(*runtime, "selector").asString(*runtime).utf8(*runtime));
      auto textStr = make_jstring(obj.getProperty(*runtime, "text").asString(*runtime).utf8(*runtime));
      auto element = emuSelectorClass->newObject(emuSelectorConstructor,
                                                 selectorStr.release(),
                                                 textStr.release());
      result->setElement(i, element.release());
    }
  }
  return result;
}


void Api::addCustomFilter(hermesptr runtime, alias_ref<JString> filter)
{
  const auto jsFilter = String::createFromAscii(*runtime, filter->toStdString());
  getFunction(runtime, JAVASCRIPT_FUNCTION_FILTER_ADD)
      .call(*runtime, jsFilter);
}

void Api::removeCustomFilter(hermesptr runtime, alias_ref<JString> filter)
{
  const auto jsFilter = String::createFromAscii(*runtime, filter->toStdString());
  getFunction(runtime, JAVASCRIPT_FUNCTION_FILTER_REMOVE)
      .call(*runtime, jsFilter);
}


jboolean Api::isListedSubscription(hermesptr runtime, alias_ref<JString> subscriptionUrl)
{
  const std::string url = subscriptionUrl->toStdString();
  if (url.empty())
  {
    return false;
  }
  const auto jsSubscriptionUrl = String::createFromAscii(*runtime, url);
  const auto value = getFunction(runtime, JAVASCRIPT_FUNCTION_SUBSC_IS_LISTED)
      .call(*runtime, jsSubscriptionUrl);
  return value.isBool() && value.getBool();
}

void Api::addSubscription(hermesptr runtime, alias_ref<JString> subscriptionUrl)
{
  const std::string url = subscriptionUrl->toStdString();
  if (url.empty())
  {
    return;
  }
  const auto jsSubscriptionUrl = String::createFromAscii(*runtime, url);
  getFunction(runtime, JAVASCRIPT_FUNCTION_SUBSC_ADD)
      .call(*runtime, jsSubscriptionUrl);
}

void Api::removeSubscription(hermesptr runtime, alias_ref<JString> subscriptionUrl)
{
  const std::string url = subscriptionUrl->toStdString();
  if (url.empty())
  {
    return;
  }
  const auto jsSubscriptionUrl = String::createFromAscii(*runtime, url);
  getFunction(runtime, JAVASCRIPT_FUNCTION_SUBSC_REMOVE)
      .call(*runtime, jsSubscriptionUrl);
}

JObjectLocalRef makeJavaSubscriptionObject(hermesptr runtime, const facebook::jsi::Object& jsSubscriptionObject)
{
  static const auto subscriptionClass = make_global(findClassLocal(JAVA_CLASS_SUBSCRIPTION));
  const auto subscriptionConstructor = subscriptionClass->getConstructor<jobject(jstring, jstring)>();

  if (!jsSubscriptionObject.hasProperty(*runtime, "url") || !jsSubscriptionObject.hasProperty(*runtime, "title"))
  {
    return subscriptionClass->newObject(subscriptionConstructor, make_jstring("").release(),
                                        make_jstring("").release());
  }

  auto urlValue = jsSubscriptionObject.getProperty(*runtime, "url");
  auto titleValue = jsSubscriptionObject.getProperty(*runtime, "title");

  if (!urlValue.isString() || !titleValue.isString())
  {
    return subscriptionClass->newObject(subscriptionConstructor, make_jstring("").release(),
                                        make_jstring("").release());
  }

  auto urlStr = make_jstring(urlValue.asString(*runtime).utf8(*runtime));
  auto titleStr = make_jstring(titleValue.asString(*runtime).utf8(*runtime));
  return subscriptionClass->newObject(subscriptionConstructor,
                                      urlStr.release(),
                                      titleStr.release());
}

JObjectArrayLocalRef Api::getListedSubscriptionUrls(hermesptr runtime, alias_ref<AdblockEngine> thiz)
{
  const auto array = getFunction(runtime, JAVASCRIPT_FUNCTION_SUBSC_LIST)
      .call(*runtime)
      .asObject(*runtime).asArray(*runtime);
  const auto arraySize = array.length(*runtime);

  const auto subscriptionClass = findClassLocal(JAVA_CLASS_SUBSCRIPTION);
  JObjectArrayLocalRef result = JObjectArray::newArray(arraySize);

  if (arraySize > 0)
  {
    for (int i = 0; i < arraySize; ++i)
    {
      const auto subscriptionObj = array.getValueAtIndex(*runtime, i).asObject(*runtime);
      result->setElement(i, makeJavaSubscriptionObject(runtime, subscriptionObj).release());
    }
  }
  return result;
}

JObjectLocalRef Api::getSubscriptionByUrl(hermesptr runtime, alias_ref<JString> subscriptionUrl)
{
  if (subscriptionUrl->toStdString().empty())
  {
    const facebook::jsi::Object nullObject(*runtime);
    return makeJavaSubscriptionObject(runtime, nullObject);
  }
  const auto jsSubscriptionUrl = String::createFromAscii(*runtime, subscriptionUrl->toStdString());
  const auto subscriptionObj = getFunction(runtime, JAVASCRIPT_FUNCTION_SUBSC_GET)
      .call(*runtime, jsSubscriptionUrl).asObject(*runtime);

  return makeJavaSubscriptionObject(runtime, subscriptionObj);
}

void Api::setAASubscriptionEnabled(hermesptr runtime, jboolean enabled)
{
  getFunction(runtime, JAVASCRIPT_FUNCTION_SUBSC_SET_AA_ENABLED)
      .call(*runtime, Value(enabled != 0));
}


Function Api::getFunction(hermesptr runtime, const char* functionName)
{
  try
  {
    const auto global = runtime->global();
    const auto api = global.getPropertyAsObject(*runtime, JAVASCRIPT_OBJECT);
    return api.getPropertyAsFunction(*runtime, functionName);
  }
  catch (const JSError& e)
  {
    throwNewJavaException("java/lang/RuntimeException", std::string(e.getMessage() + "\n" + e.getStack()).c_str());
  }
  catch (const std::exception& e)
  {
    throwNewJavaException("java/lang/RuntimeException", e.what());
  }
}
