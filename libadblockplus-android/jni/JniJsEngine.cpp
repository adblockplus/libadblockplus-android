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
#include <AdblockPlus/DefaultFileSystem.h>
#include "Utils.h"
#include "JniCallbacks.h"
#include "JniJsEngine.h"

static void TransformAppInfo(JNIEnv* env, jobject jAppInfo, AdblockPlus::AppInfo& appInfo)
{
  jclass clazz = env->GetObjectClass(jAppInfo);

  appInfo.application = JniGetStringField(env, clazz, jAppInfo, "application");
  appInfo.applicationVersion = JniGetStringField(env, clazz, jAppInfo, "applicationVersion");
  appInfo.locale = JniGetStringField(env, clazz, jAppInfo, "locale");
  appInfo.name = JniGetStringField(env, clazz, jAppInfo, "name");
  appInfo.version = JniGetStringField(env, clazz, jAppInfo, "version");

  appInfo.developmentBuild = JniGetBooleanField(env, clazz, jAppInfo, "developmentBuild");
}

static AdblockPlus::JsEngine& GetJsEngineRef(jlong ptr)
{
  return *JniLongToTypePtr<JniJsEngine>(ptr)->jsEngine;
}

static jlong JNICALL JniCtor(JNIEnv* env, jclass clazz, jobject jAppInfo)
{
  AdblockPlus::AppInfo appInfo;

  TransformAppInfo(env, jAppInfo, appInfo);

  try
  {
    AdblockPlus::TimerPtr timer = AdblockPlus::CreateDefaultTimer();
    JniJsEngine* jniJsEngine = new JniJsEngine();
    jniJsEngine->timer = timer.get();
    jniJsEngine->jsEngine = AdblockPlus::JsEngine::New(appInfo, std::move(timer));
    return JniPtrToLong(jniJsEngine);
  }
  CATCH_THROW_AND_RETURN(env, 0)
}

static void JNICALL JniDtor(JNIEnv* env, jclass clazz, jlong ptr)
{
  delete JniLongToTypePtr<JniJsEngine>(ptr);
}

static void JNICALL JniSetEventCallback(JNIEnv* env, jclass clazz, jlong ptr, jstring jEventName, jlong jCallbackPtr)
{
  AdblockPlus::JsEngine& engine = GetJsEngineRef(ptr);

  JniEventCallback* callback = JniLongToTypePtr<JniEventCallback>(jCallbackPtr);
  std::string eventName = JniJavaToStdString(env, jEventName);

  auto eCallback = [callback](AdblockPlus::JsValueList&& params)
  {
    callback->Callback(std::move(params));
  };

  try
  {
    engine.SetEventCallback(eventName, eCallback);
  }
  CATCH_AND_THROW(env)
}

static void JNICALL JniRemoveEventCallback(JNIEnv* env, jclass clazz, jlong ptr, jstring jEventName)
{
  AdblockPlus::JsEngine& engine = GetJsEngineRef(ptr);

  std::string eventName = JniJavaToStdString(env, jEventName);

  try
  {
    engine.RemoveEventCallback(eventName);
  }
  CATCH_AND_THROW(env)
}

static jobject JNICALL JniEvaluate(JNIEnv* env, jclass clazz, jlong ptr, jstring jSource, jstring jFilename)
{
  AdblockPlus::JsEngine& engine = GetJsEngineRef(ptr);

  std::string source = JniJavaToStdString(env, jSource);
  std::string filename = JniJavaToStdString(env, jFilename);

  try
  {
    return NewJniJsValue(env, engine.Evaluate(source, filename));
  }
  CATCH_THROW_AND_RETURN(env, 0)
}

static void JNICALL JniTriggerEvent(JNIEnv* env, jclass clazz, jlong ptr, jstring jEventName, jarray jJsPtrs)
{
  AdblockPlus::JsEngine& engine = GetJsEngineRef(ptr);
  std::string eventName = JniJavaToStdString(env, jEventName);
  AdblockPlus::JsValueList args;

  if (jJsPtrs)
  {
    jlong* ptrs = (jlong*)env->GetPrimitiveArrayCritical(jJsPtrs, 0);

    jsize length = env->GetArrayLength(jJsPtrs);

    for (jsize i = 0; i < length; i++)
    {
      args.push_back(JniGetJsValue(ptrs[i]));
    }

    env->ReleasePrimitiveArrayCritical(jJsPtrs, ptrs, JNI_ABORT);
  }

  try
  {
    engine.TriggerEvent(eventName, std::move(args));
  }
  CATCH_AND_THROW(env)
}

static void JNICALL JniSetDefaultFileSystem(JNIEnv* env, jclass clazz, jlong ptr, jstring jBasePath)
{
  AdblockPlus::JsEngine& engine = GetJsEngineRef(ptr);

  try
  {
    auto fileSystem = std::make_shared<AdblockPlus::DefaultFileSystemSync>();

    std::string basePath = JniJavaToStdString(env, jBasePath);
    fileSystem->SetBasePath(basePath);

    engine.SetFileSystem(fileSystem);
  }
  CATCH_AND_THROW(env)
}

static void JNICALL JniSetDefaultLogSystem(JNIEnv* env, jclass clazz, jlong ptr)
{
  AdblockPlus::JsEngine& engine = GetJsEngineRef(ptr);

  try
  {
    AdblockPlus::LogSystemPtr logSystem(new AdblockPlus::DefaultLogSystem());

    engine.SetLogSystem(logSystem);
  }
  CATCH_AND_THROW(env)
}

static void JNICALL JniSetLogSystem(JNIEnv* env, jclass clazz, jlong ptr, jlong logSystemPtr)
{
  AdblockPlus::JsEngine& engine = GetJsEngineRef(ptr);

  try
  {
    AdblockPlus::LogSystemPtr logSystem = *JniLongToTypePtr<AdblockPlus::LogSystemPtr>(logSystemPtr);

    engine.SetLogSystem(logSystem);
  }
  CATCH_AND_THROW(env)
}

static void JNICALL JniSetWebRequest(JNIEnv* env, jclass clazz, jlong ptr, jlong webRequestPtr)
{
  AdblockPlus::JsEngine& engine = GetJsEngineRef(ptr);

  try
  {
    auto& webRequest = *JniLongToTypePtr<AdblockPlus::WebRequestSharedPtr>(webRequestPtr);

    engine.SetWebRequest(webRequest);
  }
  CATCH_AND_THROW(env)
}

static jobject JNICALL JniNewLongValue(JNIEnv* env, jclass clazz, jlong ptr, jlong value)
{
  AdblockPlus::JsEngine& engine = GetJsEngineRef(ptr);

  try
  {
    return NewJniJsValue(env, engine.NewValue(static_cast<int64_t>(value)));
  }
  CATCH_THROW_AND_RETURN(env, 0)
}

static jobject JNICALL JniNewBooleanValue(JNIEnv* env, jclass clazz, jlong ptr, jboolean value)
{
  AdblockPlus::JsEngine& engine = GetJsEngineRef(ptr);

  try
  {
    return NewJniJsValue(env, engine.NewValue(value == JNI_TRUE ? true : false));
  }
  CATCH_THROW_AND_RETURN(env, 0)
}

static jobject JNICALL JniNewStringValue(JNIEnv* env, jclass clazz, jlong ptr, jstring value)
{
  AdblockPlus::JsEngine& engine = GetJsEngineRef(ptr);

  try
  {
    std::string strValue = JniJavaToStdString(env, value);
    return NewJniJsValue(env, engine.NewValue(strValue));
  }
  CATCH_THROW_AND_RETURN(env, 0)
}

// TODO: List of functions that lack JNI bindings
//JsValuePtr NewObject();
//JsValuePtr NewCallback(v8::InvocationCallback callback);
//static JsEnginePtr FromArguments(const v8::Arguments& arguments);
//JsValueList ConvertArguments(const v8::Arguments& arguments);

static JNINativeMethod methods[] =
{
  { (char*)"ctor", (char*)"(" TYP("AppInfo") ")J", (void*)JniCtor },
  { (char*)"dtor", (char*)"(J)V", (void*)JniDtor },

  { (char*)"setEventCallback", (char*)"(JLjava/lang/String;J)V", (void*)JniSetEventCallback },
  { (char*)"removeEventCallback", (char*)"(JLjava/lang/String;)V", (void*)JniRemoveEventCallback },
  { (char*)"triggerEvent", (char*)"(JLjava/lang/String;[J)V", (void*)JniTriggerEvent },

  { (char*)"evaluate", (char*)"(JLjava/lang/String;Ljava/lang/String;)" TYP("JsValue"), (void*)JniEvaluate },

  { (char*)"setDefaultFileSystem", (char*)"(JLjava/lang/String;)V", (void*)JniSetDefaultFileSystem },
  { (char*)"setLogSystem", (char*)"(JJ)V", (void*)JniSetLogSystem },
  { (char*)"setDefaultLogSystem", (char*)"(J)V", (void*)JniSetDefaultLogSystem },
  { (char*)"setWebRequest", (char*)"(JJ)V", (void*)JniSetWebRequest },

  { (char*)"newValue", (char*)"(JJ)" TYP("JsValue"), (void*)JniNewLongValue },
  { (char*)"newValue", (char*)"(JZ)" TYP("JsValue"), (void*)JniNewBooleanValue },
  { (char*)"newValue", (char*)"(JLjava/lang/String;)" TYP("JsValue"), (void*)JniNewStringValue }
};

extern "C" JNIEXPORT void JNICALL Java_org_adblockplus_libadblockplus_JsEngine_registerNatives(JNIEnv *env, jclass clazz)
{
  env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0]));
}
