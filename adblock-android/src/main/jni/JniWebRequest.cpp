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

#include "JniCallbacks.h"
#include "JniWebRequest.h"

// precached in JNI_OnLoad and released in JNI_OnUnload
JniGlobalReference<jclass>* headerEntryClass;
JniGlobalReference<jclass>* serverResponseClass;

JniGlobalReference<jclass>* webRequestCallbackClass;
jmethodID callbackClassCtor;


void JniWebRequest_OnLoad(JavaVM* vm, JNIEnv* env, void* reserved)
{
  headerEntryClass = new JniGlobalReference<jclass>(env, env->FindClass(PKG("HeaderEntry")));
  serverResponseClass = new JniGlobalReference<jclass>(env, env->FindClass(PKG("ServerResponse")));

  webRequestCallbackClass = new JniGlobalReference<jclass>(env, env->FindClass(PKG("WebRequest$Callback")));
  callbackClassCtor = env->GetMethodID(webRequestCallbackClass->Get(), "<init>", "(J)V");
}

void JniWebRequest_OnUnload(JavaVM* vm, JNIEnv* env, void* reserved)
{
  if (headerEntryClass)
  {
    delete headerEntryClass;
    headerEntryClass = NULL;
  }

  if (serverResponseClass)
  {
    delete serverResponseClass;
    serverResponseClass = NULL;
  }

  if (webRequestCallbackClass)
  {
    delete webRequestCallbackClass;
    webRequestCallbackClass = NULL;
  }
}

JniWebRequestCallback::JniWebRequestCallback(JNIEnv* env, jobject callbackObject)
  : JniCallbackBase(env, callbackObject)
{
}

void JniWebRequestCallback::GET(const std::string& url,
         const AdblockPlus::HeaderList& requestHeaders,
         const AdblockPlus::IWebRequest::GetCallback& getCallback)
{
  JNIEnvAcquire env(GetJavaVM());

  jmethodID method = env->GetMethodID(
      *JniLocalReference<jclass>(*env, env->GetObjectClass(GetCallbackObject())),
      "GET",
      "(Ljava/lang/String;Ljava/util/List;" TYP("WebRequest$Callback") ")V" );

  if (method)
  {
    jstring jUrl = JniStdStringToJava(*env, url);

    JniLocalReference<jobject> jHeaders(*env, NewJniArrayList(*env));
    jmethodID addMethod = JniGetAddToListMethod(*env, *jHeaders);

    for (AdblockPlus::HeaderList::const_iterator it = requestHeaders.begin(),
        end = requestHeaders.end(); it != end; it++)
    {
      JniLocalReference<jobject> headerEntry(*env, NewTuple(*env, it->first, it->second));
      JniAddObjectToList(*env, *jHeaders, addMethod, *headerEntry);
    }

    jobject jCallback = env->NewObject(
        webRequestCallbackClass->Get(),
        callbackClassCtor,
        JniPtrToLong(new AdblockPlus::IWebRequest::GetCallback(getCallback)));

    jvalue args[3];
    args[0].l = jUrl;
    args[1].l = *jHeaders;
    args[2].l = jCallback;
    env->CallVoidMethodA(GetCallbackObject(), method, args);

    if (CheckAndLogJavaException(*env))
    {
      AdblockPlus::ServerResponse response;
      response.status = AdblockPlus::IWebRequest::NS_ERROR_FAILURE;
      getCallback(response);
    }
  }
}

jobject JniWebRequestCallback::NewTuple(JNIEnv* env, const std::string& a,
    const std::string& b) const
{
  jmethodID factory = env->GetMethodID(headerEntryClass->Get(), "<init>",
      "(Ljava/lang/String;Ljava/lang/String;)V");

  JniLocalReference<jstring> strA(env, env->NewStringUTF(a.c_str()));
  JniLocalReference<jstring> strB(env, env->NewStringUTF(b.c_str()));

  return env->NewObject(headerEntryClass->Get(), factory, *strA, *strB);
}

static void JNICALL JniCallbackOnFinished(JNIEnv* env, jclass clazz, jlong ptr, jobject response)
{
  AdblockPlus::ServerResponse sResponse;
  sResponse.status = AdblockPlus::IWebRequest::NS_ERROR_FAILURE;

  if (response)
  {
    sResponse.status = JniGetLongField(env, serverResponseClass->Get(), response, "status");
    sResponse.responseStatus = JniGetIntField(env,
                                              serverResponseClass->Get(),
                                              response,
                                              "responseStatus");
    sResponse.responseText = JniGetStringField(env,
                                               serverResponseClass->Get(),
                                               response,
                                               "response");

    // map headers
    jobjectArray responseHeadersArray = JniGetStringArrayField(env,
                                                               serverResponseClass->Get(),
                                                               response,
                                                               "headers");

    if (responseHeadersArray)
    {
      int itemsCount = env->GetArrayLength(responseHeadersArray) / 2;
      for (int i = 0; i < itemsCount; i++)
      {
        jstring jKey = (jstring) env->GetObjectArrayElement(responseHeadersArray, i * 2);
        std::string stdKey = JniJavaToStdString(env, jKey);

        jstring jValue = (jstring) env->GetObjectArrayElement(responseHeadersArray, i * 2 + 1);
        std::string stdValue = JniJavaToStdString(env, jValue);

        sResponse.responseHeaders.push_back(std::make_pair(stdKey, stdValue));
      }
    }
  }

  (*JniLongToTypePtr<AdblockPlus::IWebRequest::GetCallback>(ptr))(sResponse);
}

static void JNICALL JniCallbackDtor(JNIEnv* env, jclass clazz, jlong ptr)
{
  delete JniLongToTypePtr<AdblockPlus::IWebRequest::GetCallback>(ptr);
}

static JNINativeMethod methods[] =
{
  { (char*)"callbackOnFinished", (char*)"(J" TYP("ServerResponse") ")V", (void*)JniCallbackOnFinished },
  { (char*)"callbackDtor", (char*)"(J)V", (void*)JniCallbackDtor }
};

extern "C" JNIEXPORT void JNICALL Java_org_adblockplus_libadblockplus_WebRequest_registerNatives(
    JNIEnv *env, jclass clazz)
{
  env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0]));
}

