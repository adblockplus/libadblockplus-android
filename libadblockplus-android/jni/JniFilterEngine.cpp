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

#include <AdblockPlus.h>
#include "Utils.h"
#include "JniCallbacks.h"
#include <thread>
#include "JniPlatform.h"

static jobject SubscriptionsToArrayList(JNIEnv* env, std::vector<AdblockPlus::Subscription>&& subscriptions)
{
  jobject list = NewJniArrayList(env);

  for (std::vector<AdblockPlus::Subscription>::iterator it = subscriptions.begin(), end = subscriptions.end(); it != end; it++)
  {
    JniAddObjectToList(env, list, NewJniSubscription(env, std::move(*it)));
  }

  return list;
}

static AdblockPlus::FilterEngine::ContentType ConvertContentType(JNIEnv *env,
    jobject jContentType)
{
  JniLocalReference<jclass> contentTypeClass(env,
      env->GetObjectClass(jContentType));
  jmethodID nameMethod = env->GetMethodID(*contentTypeClass, "name",
      "()Ljava/lang/String;");
  JniLocalReference<jstring> jValue(env,
      (jstring) env->CallObjectMethod(jContentType, nameMethod));
  const std::string value = JniJavaToStdString(env, *jValue);
  return AdblockPlus::FilterEngine::StringToContentType(value);
}

namespace
{
  AdblockPlus::FilterEngine& GetFilterEngineRef(jlong jniPlatformPtr)
  {
    return JniLongToTypePtr<JniPlatform>(jniPlatformPtr)->platform->GetFilterEngine();
  }
}

static jboolean JNICALL JniIsFirstRun(JNIEnv* env, jclass clazz, jlong ptr)
{
  try
  {
    AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);

    return engine.IsFirstRun() ? JNI_TRUE : JNI_FALSE;
  }
  CATCH_THROW_AND_RETURN(env, JNI_FALSE);
}

static jobject JNICALL JniGetFilter(JNIEnv* env, jclass clazz, jlong ptr, jstring jText)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);
  std::string text = JniJavaToStdString(env, jText);

  try
  {
    return NewJniFilter(env, engine.GetFilter(text));
  }
  CATCH_THROW_AND_RETURN(env, 0);
}

static jobject JNICALL JniGetListedFilters(JNIEnv* env, jclass clazz, jlong ptr)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);

  try
  {
    std::vector<AdblockPlus::Filter> filters = engine.GetListedFilters();

    jobject list = NewJniArrayList(env);

    for (std::vector<AdblockPlus::Filter>::iterator it = filters.begin(), end = filters.end(); it != end; it++)
    {
      JniAddObjectToList(env, list, *JniLocalReference<jobject>(env, NewJniFilter(env, std::move(*it))));
    }

    return list;
  }
  CATCH_THROW_AND_RETURN(env, 0);
}

static jobject JNICALL JniGetSubscription(JNIEnv* env, jclass clazz, jlong ptr, jstring jUrl)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);
  std::string url = JniJavaToStdString(env, jUrl);

  try
  {
    return NewJniSubscription(env, engine.GetSubscription(url));
  }
  CATCH_THROW_AND_RETURN(env, 0);
}

static void JNICALL JniShowNextNotification(JNIEnv* env, jclass clazz, jlong ptr, jstring jUrl)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);
  std::string url = JniJavaToStdString(env, jUrl);

  try
  {
    engine.ShowNextNotification(url);
  }
  CATCH_AND_THROW(env);
}

static void JNICALL JniSetShowNotificationCallback(JNIEnv* env, jclass clazz,
                                                  jlong ptr, jlong callbackPtr)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);

  JniShowNotificationCallback* const callback =
      JniLongToTypePtr<JniShowNotificationCallback>(callbackPtr);

  auto showNotificationCallback = [callback](AdblockPlus::Notification&& notification)
  {
    callback->Callback(std::move(notification));
  };

  try
  {
    engine.SetShowNotificationCallback(showNotificationCallback);
  }
  CATCH_AND_THROW(env)
}

static void JNICALL JniRemoveShowNotificationCallback(JNIEnv* env, jclass clazz, jlong ptr)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);

  try
  {
    engine.RemoveShowNotificationCallback();
  }
  CATCH_AND_THROW(env);
}

static jobject JNICALL JniGetListedSubscriptions(JNIEnv* env, jclass clazz, jlong ptr)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);

  try
  {
    return SubscriptionsToArrayList(env, engine.GetListedSubscriptions());
  }
  CATCH_THROW_AND_RETURN(env, 0);
}

static jobject JNICALL JniFetchAvailableSubscriptions(JNIEnv* env, jclass clazz, jlong ptr)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);

  try
  {
    return SubscriptionsToArrayList(env, engine.FetchAvailableSubscriptions());
  }
  CATCH_THROW_AND_RETURN(env, 0);
}

static void JNICALL JniRemoveUpdateAvailableCallback(JNIEnv* env, jclass clazz,
                                                     jlong ptr)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);
  try
  {
    engine.RemoveUpdateAvailableCallback();
  }
  CATCH_AND_THROW(env)
}

static void JNICALL JniSetUpdateAvailableCallback(JNIEnv* env, jclass clazz,
                                                  jlong ptr, jlong callbackPtr)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);
  JniUpdateAvailableCallback* const callback =
      JniLongToTypePtr<JniUpdateAvailableCallback>(callbackPtr);

  const AdblockPlus::FilterEngine::UpdateAvailableCallback updateAvailableCallback =
      std::bind(&JniUpdateAvailableCallback::Callback, callback,
                     std::placeholders::_1);
  try
  {
    engine.SetUpdateAvailableCallback(updateAvailableCallback);
  }
  CATCH_AND_THROW(env)
}

static void JNICALL JniRemoveFilterChangeCallback(JNIEnv* env, jclass clazz, jlong ptr)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);

  try
  {
    engine.RemoveFilterChangeCallback();
  }
  CATCH_AND_THROW(env)
}

static void JNICALL JniSetFilterChangeCallback(JNIEnv* env, jclass clazz,
    jlong ptr, jlong filterPtr)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);
  JniFilterChangeCallback* callback = JniLongToTypePtr<JniFilterChangeCallback>(
      filterPtr);

  auto filterCallback = [callback](const std::string& arg, AdblockPlus::JsValue&& jsValue)
  {
    callback->Callback(arg, std::move(jsValue));
  };

  try
  {
    engine.SetFilterChangeCallback(filterCallback);
  }
  CATCH_AND_THROW(env)
}

static void JNICALL JniForceUpdateCheck(JNIEnv* env, jclass clazz, jlong ptr, jlong updaterPtr)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);
  JniUpdateCheckDoneCallback* callback =
      JniLongToTypePtr<JniUpdateCheckDoneCallback>(updaterPtr);

  AdblockPlus::FilterEngine::UpdateCheckDoneCallback
      updateCheckDoneCallback = 0;

  if (updaterPtr)
  {
    updateCheckDoneCallback =
        std::bind(&JniUpdateCheckDoneCallback::Callback, callback,
                       std::placeholders::_1);
  }

  try
  {
    engine.ForceUpdateCheck(updateCheckDoneCallback);
  }
  CATCH_AND_THROW(env)
}

static jobject JNICALL JniGetElementHidingSelectors(JNIEnv* env, jclass clazz,
    jlong ptr, jstring jDomain)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);

  std::string domain = JniJavaToStdString(env, jDomain);

  try
  {
    std::vector<std::string> selectors = engine.GetElementHidingSelectors(
        domain);

    jobject list = NewJniArrayList(env);

    for (std::vector<std::string>::iterator it = selectors.begin(), end =
        selectors.end(); it != end; it++)
    {
      JniAddObjectToList(env, list,
          *JniLocalReference<jstring>(env, env->NewStringUTF(it->c_str())));
    }

    return list;
  }
  CATCH_THROW_AND_RETURN(env, 0)
}

static jobject JNICALL JniMatches(JNIEnv* env, jclass clazz, jlong ptr, jstring jUrl, jobject jContentType, jstring jDocumentUrl)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);

  std::string url = JniJavaToStdString(env, jUrl);
  AdblockPlus::FilterEngine::ContentType contentType =
      ConvertContentType(env, jContentType);
  std::string documentUrl = JniJavaToStdString(env, jDocumentUrl);

  try
  {
    AdblockPlus::FilterPtr filterPtr = engine.Matches(url, contentType, documentUrl);

    return filterPtr.get() ? NewJniFilter(env, std::move(*filterPtr)) : 0;
  }
  CATCH_THROW_AND_RETURN(env, 0)
}

static void JavaStringArrayToStringVector(JNIEnv* env, jobjectArray jArray,
    std::vector<std::string>& out)
{
  if (jArray)
  {
    jsize len = env->GetArrayLength(jArray);

    for (jsize i = 0; i < len; i++)
    {
      out.push_back(
          JniJavaToStdString(env,
              *JniLocalReference<jstring>(env,
                  static_cast<jstring>(
                      env->GetObjectArrayElement(jArray, i)))));
    }
  }
}

static jobject JNICALL JniMatchesMany(JNIEnv* env, jclass clazz, jlong ptr,
    jstring jUrl, jobject jContentType, jobjectArray jDocumentUrls)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);

  std::string url = JniJavaToStdString(env, jUrl);
  AdblockPlus::FilterEngine::ContentType contentType =
      ConvertContentType(env, jContentType);

  std::vector<std::string> documentUrls;
  JavaStringArrayToStringVector(env, jDocumentUrls, documentUrls);

  try
  {
    AdblockPlus::FilterPtr filterPtr = engine.Matches(url, contentType, documentUrls);

    return (filterPtr.get() ? NewJniFilter(env, std::move(*filterPtr)) : 0);
  }
  CATCH_THROW_AND_RETURN(env, 0)
}

static jboolean JNICALL JniIsDocumentWhitelisted(JNIEnv* env, jclass clazz, jlong ptr,
    jstring jUrl, jobjectArray jDocumentUrls)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);

  std::string url = JniJavaToStdString(env, jUrl);
  std::vector<std::string> documentUrls;
  JavaStringArrayToStringVector(env, jDocumentUrls, documentUrls);
  try
  {
    return engine.IsDocumentWhitelisted(url, documentUrls) ?
        JNI_TRUE : JNI_FALSE;
  }
  CATCH_THROW_AND_RETURN(env, JNI_FALSE)
}

static jboolean JNICALL JniIsElemhideWhitelisted(JNIEnv* env, jclass clazz, jlong ptr,
    jstring jUrl, jobjectArray jDocumentUrls)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);

  std::string url = JniJavaToStdString(env, jUrl);
  std::vector<std::string> documentUrls;
  JavaStringArrayToStringVector(env, jDocumentUrls, documentUrls);
  try
  {
    return engine.IsElemhideWhitelisted(url, documentUrls) ?
        JNI_TRUE : JNI_FALSE;
  }
  CATCH_THROW_AND_RETURN(env, JNI_FALSE)
}

static jobject JNICALL JniGetPref(JNIEnv* env, jclass clazz, jlong ptr, jstring jPref)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);

  std::string pref = JniJavaToStdString(env, jPref);

  try
  {
    return NewJniJsValue(env, engine.GetPref(pref));
  }
  CATCH_THROW_AND_RETURN(env, 0)
}

static void JNICALL JniSetPref(JNIEnv* env, jclass clazz, jlong ptr, jstring jPref, jlong jsValue)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);

  std::string pref = JniJavaToStdString(env, jPref);
  const AdblockPlus::JsValue& value = JniGetJsValue(jsValue);

  try
  {
    engine.SetPref(pref, value);
  }
  CATCH_AND_THROW(env)
}

static jstring JNICALL JniGetHostFromURL(JNIEnv* env, jclass clazz, jlong ptr, jstring jurl)
{
  if (jurl == NULL)
  {
    return NULL;
  }

  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);

  std::string url = JniJavaToStdString(env, jurl);
  try
  {
    std::string host = engine.GetHostFromURL(url);

    return JniStdStringToJava(env, host);
  }
  CATCH_THROW_AND_RETURN(env, 0)
}

static void JNICALL JniSetAllowedConnectionType(JNIEnv* env, jclass clazz, jlong ptr, jstring jvalue)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);

  std::string stdValue;
  const std::string* value = (jvalue != NULL
    ? &(stdValue = JniJavaToStdString(env, jvalue))
    : NULL);

  try
  {
    engine.SetAllowedConnectionType(value);
  }
  CATCH_AND_THROW(env)
}

static jstring JNICALL JniGetAllowedConnectionType(JNIEnv* env, jclass clazz, jlong ptr)
{
  try
  {
    AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);
    std::unique_ptr<std::string> value = engine.GetAllowedConnectionType();

    if (value == NULL)
    {
      return NULL;
    }

    return JniStdStringToJava(env, *value.get());
  }
  CATCH_THROW_AND_RETURN(env, 0)
}

static void JNICALL JniSetAcceptableAdsEnabled(JNIEnv* env, jclass clazz, jlong ptr, jboolean jvalue)
{
  AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);

  try
  {
    engine.SetAAEnabled(jvalue == JNI_TRUE);
  }
  CATCH_AND_THROW(env)
}

static jboolean JNICALL JniIsAcceptableAdsEnabled(JNIEnv* env, jclass clazz, jlong ptr)
{
  try
  {
    AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);
    return engine.IsAAEnabled() ? JNI_TRUE : JNI_FALSE;
  }
  CATCH_THROW_AND_RETURN(env, 0)
}

static jstring JNICALL JniGetAcceptableAdsSubscriptionURL(JNIEnv* env, jclass clazz, jlong ptr)
{
  try
  {
    AdblockPlus::FilterEngine& engine = GetFilterEngineRef(ptr);
    std::string url = engine.GetAAUrl();
    return JniStdStringToJava(env, url);
  }
  CATCH_THROW_AND_RETURN(env, 0)
}

static void JNICALL JniUpdateFiltersAsync(JNIEnv* env, jclass clazz, jlong jniPlatformPtr, jstring jSubscriptionUrl)
{
  std::string subscriptionUrl = JniJavaToStdString(env, jSubscriptionUrl);
  auto jniPlatform = JniLongToTypePtr<JniPlatform>(jniPlatformPtr);
  jniPlatform->scheduler([jniPlatform, subscriptionUrl]
  {
    auto& filterEngine = jniPlatform->platform->GetFilterEngine();
    for (auto& subscription : filterEngine.GetListedSubscriptions())
    {
      if (stringBeginsWith(subscriptionUrl, subscription.GetProperty("url").AsString()))
      {
        subscription.UpdateFilters();
        return;
      }
    }
  });
}

static JNINativeMethod methods[] =
{
  { (char*)"isFirstRun", (char*)"(J)Z", (void*)JniIsFirstRun },
  { (char*)"getFilter", (char*)"(JLjava/lang/String;)" TYP("Filter"), (void*)JniGetFilter },
  { (char*)"getListedFilters", (char*)"(J)Ljava/util/List;", (void*)JniGetListedFilters },
  { (char*)"getSubscription", (char*)"(JLjava/lang/String;)" TYP("Subscription"), (void*)JniGetSubscription },
  { (char*)"showNextNotification", (char*)"(JLjava/lang/String;)V", (void*)JniShowNextNotification },
  { (char*)"setShowNotificationCallback", (char*)"(JJ)V", (void*)JniSetShowNotificationCallback },
  { (char*)"removeShowNotificationCallback", (char*)"(J)V", (void*)JniRemoveShowNotificationCallback },
  { (char*)"getListedSubscriptions", (char*)"(J)Ljava/util/List;", (void*)JniGetListedSubscriptions },
  { (char*)"fetchAvailableSubscriptions", (char*)"(J)Ljava/util/List;", (void*)JniFetchAvailableSubscriptions },
  { (char*)"setUpdateAvailableCallback", (char*)"(JJ)V", (void*)JniSetUpdateAvailableCallback },
  { (char*)"removeUpdateAvailableCallback", (char*)"(J)V", (void*)JniRemoveUpdateAvailableCallback },
  { (char*)"setFilterChangeCallback", (char*)"(JJ)V", (void*)JniSetFilterChangeCallback },
  { (char*)"removeFilterChangeCallback", (char*)"(J)V", (void*)JniRemoveFilterChangeCallback },
  { (char*)"forceUpdateCheck", (char*)"(JJ)V", (void*)JniForceUpdateCheck },
  { (char*)"getElementHidingSelectors", (char*)"(JLjava/lang/String;)Ljava/util/List;", (void*)JniGetElementHidingSelectors },
  { (char*)"matches", (char*)"(JLjava/lang/String;" TYP("FilterEngine$ContentType") "Ljava/lang/String;)" TYP("Filter"), (void*)JniMatches },
  { (char*)"matches", (char*)"(JLjava/lang/String;" TYP("FilterEngine$ContentType") "[Ljava/lang/String;)" TYP("Filter"), (void*)JniMatchesMany },
  { (char*)"isDocumentWhitelisted", (char*)"(JLjava/lang/String;[Ljava/lang/String;)Z", (void*)JniIsDocumentWhitelisted },
  { (char*)"isElemhideWhitelisted", (char*)"(JLjava/lang/String;[Ljava/lang/String;)Z", (void*)JniIsElemhideWhitelisted },
  { (char*)"getPref", (char*)"(JLjava/lang/String;)" TYP("JsValue"), (void*)JniGetPref },
  { (char*)"setPref", (char*)"(JLjava/lang/String;J)V", (void*)JniSetPref },
  { (char*)"getHostFromURL", (char*)"(JLjava/lang/String;)Ljava/lang/String;", (void*)JniGetHostFromURL },
  { (char*)"setAllowedConnectionType", (char*)"(JLjava/lang/String;)V", (void*)JniSetAllowedConnectionType },
  { (char*)"getAllowedConnectionType", (char*)"(J)Ljava/lang/String;", (void*)JniGetAllowedConnectionType },
  { (char*)"setAcceptableAdsEnabled", (char*)"(JZ)V", (void*)JniSetAcceptableAdsEnabled },
  { (char*)"isAcceptableAdsEnabled", (char*)"(J)Z", (void*)JniIsAcceptableAdsEnabled },
  { (char*)"getAcceptableAdsSubscriptionURL", (char*)"(J)Ljava/lang/String;", (void*)JniGetAcceptableAdsSubscriptionURL },
  { (char*)"updateFiltersAsync", (char*)"(JLjava/lang/String;)V", (void*)JniUpdateFiltersAsync }
};

extern "C" JNIEXPORT void JNICALL Java_org_adblockplus_libadblockplus_FilterEngine_registerNatives(JNIEnv *env, jclass clazz)
{
  env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0]));
}
