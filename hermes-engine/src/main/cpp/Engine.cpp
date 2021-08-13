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

#include "Engine.h"
#include "Api.h"
#include "GlobalJsObject.h"
#include "JsFileSystem.h"
#include "JSFunctionWrapper.h"
#include "hermes/hermes.h"

#include "MappedFileStorage.h"

#include <assert.h>
#include <mutex>
#include <future>

using namespace facebook::hermes;
using namespace facebook::jsi;
using namespace AdblockPlus; // would be nice to put everything into AdblockPlus namespace

namespace
{
   // engineRef is stored because when GlobalJsObject wants to schedule setTimeout it needs to call AdblockEngine::schedule
   // non static method. There should be a better way => we will address that separately.
   global_ref<AdblockEngine> engineRef;
   std::recursive_mutex engineMutex;
   std::promise<bool> initializationPromise;

   void TriggerJsCallback(Runtime& rt, JSFunctionWrapperNative *jsFunctionWrapperNative)
   {
     const auto& argumentsList = jsFunctionWrapperNative->jsCallbackArguments;
     std::lock_guard<std::recursive_mutex> lock(engineMutex);
     const auto& jsFunction = argumentsList[0].asObject(rt).asFunction(rt);
     size_t count = argumentsList.size();
     // There is at least one value - a Function
     if (count == 1) // No Function arguments
     {
       jsFunction.call(rt);
     }
     else
     {
       const Value* args[count - 1];
       for (size_t i = 1; i < count; ++i)
       {
         args[i - 1] = &(argumentsList[i]);
       }
       jsFunction.call(rt, args[0], count - 1);
     }
     delete jsFunctionWrapperNative;
   }

   void registerJsObjects(Runtime *pRuntime, const std::string& baseDataFolder)
   {
     JsFileSystem::Setup(pRuntime, baseDataFolder);
     GlobalJsObject::Setup(pRuntime);
   }
}

void AdblockEngine::InitDone(bool success)
{
  initializationPromise.set_value(success);
}

void AdblockEngine::init(alias_ref<AdblockEngine> thiz, alias_ref<JString> baseDataFolder, alias_ref<JString> coreJsFilePath)
{
  initializationPromise = std::promise<bool>();
  auto result = initializationPromise.get_future();
  {
    std::lock_guard<std::recursive_mutex> lock(engineMutex);
    auto uniquePtr = makeHermesRuntime();
    auto pRuntime = uniquePtr.release();

    // Store the pointer to the HermesRuntime on Java side
    const auto field = thiz->getClass()->getField<jlong>("nativePtr");
    thiz->setFieldValue(field, reinterpret_cast<jlong>(pRuntime));

    const std::string baseDataPath =
            String::createFromAscii(*pRuntime, baseDataFolder->toStdString()).utf8(*pRuntime);

    registerJsObjects(pRuntime, baseDataPath);
    engineRef = make_global(thiz);

    const std::string jsPath =
            String::createFromAscii(*pRuntime, coreJsFilePath->toStdString()).utf8(*pRuntime);

    // Let's load the compiled Core bundle
    pRuntime->evaluateJavaScript(std::make_unique<MappedFileStorage>(jsPath), "");
  }
  // Wait for result, when logging works we should at least log a failure
  #ifdef NDEBUG
  result.get();
  #else
  assert(result.get() || !"Failed to load JS Core!");
  #endif
}

// TODO evaluateJS should handle several cases of returned values, not only strings
// this might be implemented through some interim state of a result, that might be converted
// later to a requested value (eg `asString()` or `asBool`) or by having several versions of
// `evaluateJS` (`evaluateJsAsBool`, `evaluateJsAsString`)
std::string AdblockEngine::evaluateJS(alias_ref<AdblockEngine> thiz, alias_ref<JString> src)
{
  std::lock_guard<std::recursive_mutex> lock(engineMutex);
  const auto field = thiz->getClass()->getField<jlong>("nativePtr");
  auto pRuntime = reinterpret_cast<hermesptr>(thiz->getFieldValue(field));

  auto buffer = std::shared_ptr<Buffer>(new StringBuffer(src->toStdString()));

  try
  {
    const auto result = pRuntime->evaluateJavaScript(buffer, "");
    const std::string &resString = result.isString() ? result.asString(*pRuntime).utf8(*pRuntime)
                                                     : result.toString(*pRuntime).utf8(*pRuntime);
    // right now we'd like to avoid returning `undefined`, but this might be a bit unexpected
    return resString != "undefined" ? resString : "";
  }
  catch (const JSError &e)
  {
    throwNewJavaException("java/lang/RuntimeException", std::string(e.getMessage() + "\n" + e.getStack()).c_str());
  }
}

void AdblockEngine::StoreCallback(Runtime& rt, const facebook::jsi::Value *args, size_t count, bool isImmediate)
{
  const JSFunctionWrapperNative *jsFunctionWrapperNative;
  {
    std::lock_guard<std::recursive_mutex> lock(engineMutex);
    jsFunctionWrapperNative = new JSFunctionWrapperNative(rt, args, count, isImmediate);
  }
  local_ref<JSFunctionWrapper> jsWrapper = JSFunctionWrapper::create((jlong)(jsFunctionWrapperNative));
  auto method = engineRef->getClass()->getMethod<void(alias_ref<JSFunctionWrapper>, jlong)>("schedule");
  method(engineRef, jsWrapper, jsFunctionWrapperNative->millis);
}

void AdblockEngine::_executeJSFunction(alias_ref<AdblockEngine> thiz, alias_ref<JSFunctionWrapper> jsFunctionWrapperRef)
{
  const auto runtimeNativePtr = thiz->getClass()->getField<jlong>("nativePtr");
  auto pRuntime = reinterpret_cast<hermesptr>(thiz->getFieldValue(runtimeNativePtr));
  const auto jsFunctionWrapperNativePtr = jsFunctionWrapperRef->getClass()->getField<jlong>("nativePtr");
  auto jsFunctionWrapperNative = reinterpret_cast<JSFunctionWrapperNative*>(
          jsFunctionWrapperRef->getFieldValue(jsFunctionWrapperNativePtr));
  TriggerJsCallback(*pRuntime, jsFunctionWrapperNative);
}

void AdblockEngine::registerNatives()
{
  javaClassStatic()->registerNatives(
      {
          makeNativeMethod("init", AdblockEngine::init),
          makeNativeMethod("evaluateJS", AdblockEngine::evaluateJS),
          makeNativeMethod("_executeJSFunction", AdblockEngine::_executeJSFunction),
          makeNativeMethod("_isContentAllowlisted", AdblockEngine::_isContentAllowlisted),
          makeNativeMethod("_matches", AdblockEngine::_matches),
          makeNativeMethod("_getElementHidingStyleSheet", AdblockEngine::_getElementHidingStyleSheet),
          makeNativeMethod("_getElementHidingEmulationSelectors", AdblockEngine::_getElementHidingEmulationSelectors),
          makeNativeMethod("_addCustomFilter", AdblockEngine::_addCustomFilter),
          makeNativeMethod("_removeCustomFilter", AdblockEngine::_removeCustomFilter),
          makeNativeMethod("_isListedSubscription", AdblockEngine::_isListedSubscription),
          makeNativeMethod("_addSubscriptionToList", AdblockEngine::_addSubscriptionToList),
          makeNativeMethod("_removeSubscriptionFromList", AdblockEngine::_removeSubscriptionFromList),
          makeNativeMethod("_getListedSubscriptions", AdblockEngine::_getListedSubscriptions),
          makeNativeMethod("_getSubscriptionByUrl", AdblockEngine::_getSubscriptionByUrl),
          makeNativeMethod("_setAASubscriptionEnabled", AdblockEngine::_setAASubscriptionEnabled),
      });
}

jboolean AdblockEngine::_isContentAllowlisted(alias_ref<AdblockEngine> thiz, int contentTypeMask,
                                       alias_ref<JList<JString> > referrerChain,
                                       alias_ref<JString> siteKey)
{
  std::lock_guard<std::recursive_mutex> lock(engineMutex);
  return Api::isContentAllowlisted(getRuntimePtr(thiz), contentTypeMask, referrerChain,
                                   siteKey);
}

std::string AdblockEngine::_matches(alias_ref<AdblockEngine> thiz, alias_ref<JString> url, int contentTypeMask,
                             alias_ref<JString> parent, alias_ref<JString> siteKey,
                             jboolean specificOnly)
{
  std::lock_guard<std::recursive_mutex> lock(engineMutex);
  return Api::matches(getRuntimePtr(thiz), url, contentTypeMask, parent, siteKey, specificOnly);
}

std::string
AdblockEngine::_getElementHidingStyleSheet(alias_ref<AdblockEngine> thiz, alias_ref<JString> domain,
                                    jboolean specificOnly)
{
  std::lock_guard<std::recursive_mutex> lock(engineMutex);
  return Api::getElementHidingStyleSheet(getRuntimePtr(thiz), domain, specificOnly);
}

JObjectArrayLocalRef AdblockEngine::_getElementHidingEmulationSelectors(alias_ref<AdblockEngine> thiz,
                                                                        alias_ref<JString> domain)
{
  std::lock_guard<std::recursive_mutex> lock(engineMutex);
  return Api::getElementHidingEmulationSelectors(getRuntimePtr(thiz), domain);
}

void AdblockEngine::_addCustomFilter(alias_ref<AdblockEngine> thiz, alias_ref<JString> filter)
{
  std::lock_guard<std::recursive_mutex> lock(engineMutex);
  Api::addCustomFilter(getRuntimePtr(thiz), filter);
}

void AdblockEngine::_removeCustomFilter(alias_ref<AdblockEngine> thiz, alias_ref<JString> filter)
{
  std::lock_guard<std::recursive_mutex> lock(engineMutex);
  Api::removeCustomFilter(getRuntimePtr(thiz), filter);
}


jboolean AdblockEngine::_isListedSubscription(alias_ref<AdblockEngine> thiz, alias_ref<JString> subscriptionUrl)
{
  std::lock_guard<std::recursive_mutex> lock(engineMutex);
  return Api::isListedSubscription(getRuntimePtr(thiz), subscriptionUrl);
}

void AdblockEngine::_addSubscriptionToList(alias_ref<AdblockEngine> thiz, alias_ref<JString> subscriptionUrl)
{
  std::lock_guard<std::recursive_mutex> lock(engineMutex);
  Api::addSubscription(getRuntimePtr(thiz), subscriptionUrl);
}

void AdblockEngine::_removeSubscriptionFromList(alias_ref<AdblockEngine> thiz, alias_ref<JString> subscriptionUrl)
{
  std::lock_guard<std::recursive_mutex> lock(engineMutex);
  Api::removeSubscription(getRuntimePtr(thiz), subscriptionUrl);
}

JObjectArrayLocalRef AdblockEngine::_getListedSubscriptions(alias_ref<AdblockEngine> thiz)
{
  std::lock_guard<std::recursive_mutex> lock(engineMutex);
  return Api::getListedSubscriptionUrls(getRuntimePtr(thiz), thiz);
}

JObjectLocalRef AdblockEngine::_getSubscriptionByUrl(alias_ref<AdblockEngine> thiz, alias_ref<JString> subscriptionUrl)
{
  std::lock_guard<std::recursive_mutex> lock(engineMutex);
  return Api::getSubscriptionByUrl(getRuntimePtr(thiz), subscriptionUrl);
}

void AdblockEngine::_setAASubscriptionEnabled(alias_ref<AdblockEngine> thiz, jboolean enabled)
{
  std::lock_guard<std::recursive_mutex> lock(engineMutex);
  Api::setAASubscriptionEnabled(getRuntimePtr(thiz), enabled);
}


hermesptr AdblockEngine::getRuntimePtr(alias_ref<AdblockEngine> thiz)
{
  const auto field = thiz->getClass()->getField<jlong>("nativePtr");
  return reinterpret_cast<hermesptr>(thiz->getFieldValue(field));
}
