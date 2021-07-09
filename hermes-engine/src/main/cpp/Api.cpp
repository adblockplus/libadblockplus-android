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
const char JAVASCRIPT_FUCTION_GET_EH_SS[] = "getElementHidingStyleSheet";
const char JAVASCRIPT_FUCTION_GET_EH_ES[] = "getElementHidingEmulationSelectors";
const char JAVASCRIPT_FUCTION_MATCHES[] = "checkFilterMatch";
const char JAVA_CLASS_EMULATIONSELECTOR[] = "org/adblockplus/EmulationSelector";

jboolean Api::isContentAllowlisted(hermesptr runtime, jint contentTypeMask,
                                   alias_ref<JList<JString> > referrerChain,
                                   alias_ref<JString> siteKey)
{
  const auto jsSiteKey = String::createFromAscii(*runtime, siteKey->toStdString());
  const auto jsContentTypeMask = Value(contentTypeMask);
  const auto nativeMatches = getFunction(runtime, JAVASCRIPT_FUCTION_MATCHES);

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
  const auto value = getFunction(runtime, JAVASCRIPT_FUCTION_MATCHES).call(*runtime, jsUrl, jsContentTypeMask,
                                                                           jsParent, jsSiteKey, jsSpecificOnly);
  return value.isString() ? value.asString(*runtime).utf8(*runtime) : "";
}

std::string Api::getElementHidingStyleSheet(hermesptr runtime, alias_ref<JString> domain, jboolean specificOnly)
{
  const auto jsDomain = String::createFromAscii(*runtime, domain->toStdString());
  const auto jsSpecificOnly = Value(specificOnly != 0);
  const auto value = getFunction(runtime, JAVASCRIPT_FUCTION_GET_EH_SS).call(*runtime, jsDomain, jsSpecificOnly);
  return value.isString() ? value.asString(*runtime).utf8(*runtime) : "";
}

JObjectArray Api::getElementHidingEmulationSelectors(hermesptr runtime, alias_ref<JString> domain)
{
  const auto jsDomain = String::createFromAscii(*runtime, domain->toStdString());
  const auto array = getFunction(runtime, JAVASCRIPT_FUCTION_GET_EH_ES).call(*runtime, jsDomain)
          .asObject(*runtime).asArray(*runtime);
  const auto arraySize = array.length(*runtime);

  const auto emuSelectorClass = findClassLocal(JAVA_CLASS_EMULATIONSELECTOR);
  JObjectArray result = JArrayClass<jobject>::newArray(arraySize);

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

Function Api::getFunction(hermesptr runtime, const char *functionName)
{
  try
  {
    const auto global = runtime->global();
    const auto api = global.getPropertyAsObject(*runtime, JAVASCRIPT_OBJECT);
    return api.getPropertyAsFunction(*runtime, functionName);
  }
  catch (const JSError &e)
  {
    throwNewJavaException("java/lang/RuntimeException", std::string(e.getMessage() + "\n" + e.getStack()).c_str());
  }
  catch (const std::exception &e)
  {
    throwNewJavaException("java/lang/RuntimeException", e.what());
  }
}
