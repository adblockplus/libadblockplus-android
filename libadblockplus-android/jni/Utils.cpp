/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-2016 Eyeo GmbH
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

#include <string>

#include "Utils.h"

// precached in JNI_OnLoad and released in JNI_OnUnload
JniGlobalReference<jclass>* arrayListClass;
jmethodID  arrayListCtor;

JniGlobalReference<jclass>* filterClass;
jmethodID filterCtor;

JniGlobalReference<jclass>* subscriptionClass;
jmethodID subscriptionCtor;

JniGlobalReference<jclass>* notificationClass;
jmethodID notificationCtor;

JniGlobalReference<jclass>* exceptionClass;

void JniUtils_OnLoad(JavaVM* vm, JNIEnv* env, void* reserved)
{
  arrayListClass = new JniGlobalReference<jclass>(env, env->FindClass("java/util/ArrayList"));
  arrayListCtor = env->GetMethodID(arrayListClass->Get(), "<init>", "()V");

  filterClass = new JniGlobalReference<jclass>(env, env->FindClass(PKG("Filter")));
  filterCtor = env->GetMethodID(filterClass->Get(), "<init>", "(J)V");

  subscriptionClass = new JniGlobalReference<jclass>(env, env->FindClass(PKG("Subscription")));
  subscriptionCtor = env->GetMethodID(subscriptionClass->Get(), "<init>", "(J)V");

  notificationClass = new JniGlobalReference<jclass>(env, env->FindClass(PKG("Notification")));
  notificationCtor = env->GetMethodID(notificationClass->Get(), "<init>", "(J)V");

  exceptionClass = new JniGlobalReference<jclass>(env, env->FindClass(PKG("AdblockPlusException")));
}

void JniUtils_OnUnload(JavaVM* vm, JNIEnv* env, void* reserved)
{
  if (arrayListClass)
  {
    delete arrayListClass;
    arrayListClass = NULL;
  }

  if (filterClass)
  {
    delete filterClass;
    filterClass = NULL;
  }

  if (subscriptionClass)
  {
    delete subscriptionClass;
    subscriptionClass = NULL;
  }

  if (notificationClass)
  {
    delete notificationClass;
    notificationClass = NULL;
  }

  if (exceptionClass)
  {
    delete exceptionClass;
    exceptionClass = NULL;
  }
}

std::string JniJavaToStdString(JNIEnv* env, jstring str)
{
  if (!str)
  {
    return std::string();
  }

  const char* cStr = env->GetStringUTFChars(str, 0);
  std::string ret(cStr);
  env->ReleaseStringUTFChars(str, cStr);

  return ret;
}

jstring JniStdStringToJava(JNIEnv* env, std::string str)
{
  return env->NewStringUTF(str.c_str());
}

jobject NewJniArrayList(JNIEnv* env)
{
  return env->NewObject(arrayListClass->Get(), arrayListCtor);
}

jmethodID JniGetAddToListMethod(JNIEnv* env, jobject list)
{
  JniLocalReference<jclass> clazz(env, env->GetObjectClass(list));
  return env->GetMethodID(*clazz, "add", "(Ljava/lang/Object;)Z");
}

void JniAddObjectToList(JNIEnv* env, jobject list, jmethodID addMethod, jobject value)
{
  env->CallBooleanMethod(list, addMethod, value);
}

void JniAddObjectToList(JNIEnv* env, jobject list, jobject value)
{
  jmethodID addMethod = JniGetAddToListMethod(env, list);
  JniAddObjectToList(env, list, addMethod, value);
}

void JniThrowException(JNIEnv* env, const std::string& message)
{
  env->ThrowNew(exceptionClass->Get(), message.c_str());
}

void JniThrowException(JNIEnv* env, const std::exception& e)
{
  JniThrowException(env, e.what());
}

void JniThrowException(JNIEnv* env)
{
  JniThrowException(env, "Unknown exception from libadblockplus");
}

JNIEnvAcquire::JNIEnvAcquire(JavaVM* javaVM)
  : javaVM(javaVM), jniEnv(0), attachmentStatus(0)
{
  attachmentStatus = javaVM->GetEnv((void **)&jniEnv, ABP_JNI_VERSION);
  if (attachmentStatus == JNI_EDETACHED)
  {
    if (javaVM->AttachCurrentThread(&jniEnv, 0))
    {
      // This one is FATAL, we can't recover from this (because without a JVM we're dead), so
      // throwing a runtime_exception in a ctor can be tolerated here IMHO
      throw std::runtime_error("Failed to get JNI environment");
    }
  }
}

JNIEnvAcquire::~JNIEnvAcquire()
{
  if (attachmentStatus == JNI_EDETACHED)
  {
    javaVM->DetachCurrentThread();
  }
}

template<typename T>
static jobject NewJniObject(JNIEnv* env, const T& value, jclass clazz, jmethodID ctor)
{
  if (!value.get())
  {
    return 0;
  }

  return env->NewObject(clazz, ctor, JniPtrToLong(new T(value)));
}

template<typename T>
static jobject NewJniObject(JNIEnv* env, const T& value, const char* javaClass)
{
  JniLocalReference<jclass> clazz( env, env->FindClass(javaClass));
  jmethodID ctor = env->GetMethodID(*clazz, "<init>", "(J)V");
  return NewJniObject(env, value, *clazz, ctor);
}

jobject NewJniFilter(JNIEnv* env, const AdblockPlus::FilterPtr& filter)
{
  return NewJniObject(env, filter, filterClass->Get(), filterCtor);
}

jobject NewJniSubscription(JNIEnv* env,
    const AdblockPlus::SubscriptionPtr& subscription)
{
  return NewJniObject(env, subscription, subscriptionClass->Get(), subscriptionCtor);
}

jobject NewJniNotification(JNIEnv* env,
    const AdblockPlus::NotificationPtr& notification)
{
  return NewJniObject(env, notification, notificationClass->Get(), notificationCtor);
}
