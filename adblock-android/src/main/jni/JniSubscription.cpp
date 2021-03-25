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
#include <numeric>
#include "Utils.h"

static AdblockPlus::Subscription GetSubscriptionPtrFromFE(JNIEnv *env, jlong ptr, jstring url)
{
  AdblockPlus::IFilterEngine* filterEngine = JniLongToTypePtr<AdblockPlus::IFilterEngine>(ptr);
  return filterEngine->GetSubscription(JniJavaToStdString(env, url));
}

static jboolean JNICALL JniIsDisabled(JNIEnv* env, jclass clazz, jlong fePtr, jstring url)
{
  try
  {
    return GetSubscriptionPtrFromFE(env, fePtr, url).IsDisabled() ? JNI_TRUE : JNI_FALSE;
  }
  CATCH_THROW_AND_RETURN(env, JNI_TRUE)
}

static void JNICALL JniSetDisabled(JNIEnv* env, jclass clazz, jlong fePtr, jboolean disabled, jstring url)
{
  try
  {
    return GetSubscriptionPtrFromFE(env, fePtr, url).SetDisabled(disabled == JNI_TRUE);
  }
  CATCH_AND_THROW(env)
}

static void JNICALL JniUpdateFilters(JNIEnv* env, jclass clazz, jlong fePtr, jstring url)
{
  try
  {
    GetSubscriptionPtrFromFE(env, fePtr, url).UpdateFilters();
  }
  CATCH_AND_THROW(env)
}

static jboolean JNICALL JniIsUpdating(JNIEnv* env, jclass clazz, jlong fePtr, jstring url)
{
  try
  {
    return GetSubscriptionPtrFromFE(env, fePtr, url).IsUpdating() ? JNI_TRUE : JNI_FALSE;
  }
  CATCH_THROW_AND_RETURN(env, JNI_FALSE)
}

static jboolean JNICALL JniIsAcceptableAds(JNIEnv* env, jclass clazz, jlong fePtr, jstring url)
{
  try
  {
    return (GetSubscriptionPtrFromFE(env, fePtr, url).IsAA() ? JNI_TRUE : JNI_FALSE);
  }
  CATCH_THROW_AND_RETURN(env, JNI_FALSE)
}

static jstring JNICALL JniGetSynchronizationStatus(JNIEnv* env, jclass clazz, jlong fePtr, jstring url)
{
  try
  {
    return env->NewStringUTF(GetSubscriptionPtrFromFE(env, fePtr, url).GetSynchronizationStatus().c_str());
  }
  CATCH_THROW_AND_RETURN(env, env->NewStringUTF(""))
}

static void JNICALL JniDtor(JNIEnv* env, jclass clazz, jlong ptr)
{
    delete JniLongToTypePtr<AdblockPlus::Subscription>(ptr);
}

static JNINativeMethod methods[] =
{
  { (char*)"isDisabled", (char*)"(JLjava/lang/String;)Z", (void*)JniIsDisabled },
  { (char*)"setDisabled", (char*)"(JZLjava/lang/String;)V", (void*)JniSetDisabled },
  { (char*)"updateFilters", (char*)"(JLjava/lang/String;)V", (void*)JniUpdateFilters },
  { (char*)"isUpdating", (char*)"(JLjava/lang/String;)Z", (void*)JniIsUpdating },
  { (char*)"isAcceptableAds", (char*)"(JLjava/lang/String;)Z", (void*)JniIsAcceptableAds },
  { (char*)"getSynchronizationStatus", (char*)"(JLjava/lang/String;)Ljava/lang/String;", (void*)JniGetSynchronizationStatus },
};

extern "C" JNIEXPORT void JNICALL Java_org_adblockplus_Subscription_registerNatives(JNIEnv *env, jclass clazz)
{
  env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0]));
}
