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

#include <string>
#include <numeric>

#include "Utils.h"
#include "JniFilter.h"

const std::string joinStringVector(const std::vector<std::string> &array, const std::string separator)
{
  return std::accumulate(std::begin(array), std::end(array),
                         std::string(),
                         [&separator](std::string &ss, const std::string &s)
                         {
                             return ss.empty() ? s : ss + separator + s;
                         });
}

// precached in JNI_OnLoad and released in JNI_OnUnload
JniGlobalReference<jclass>* arrayListClass;
jmethodID  arrayListCtor;

JniGlobalReference<jclass>* filterClass;
jmethodID filterCtor;

JniGlobalReference<jclass>* subscriptionClass;
jmethodID subscriptionCtor;

JniGlobalReference<jclass>* emulationSelectorClass;
jmethodID emulationSelectorCtor;

JniGlobalReference<jclass>* exceptionClass;

void JniUtils_OnLoad(JavaVM* vm, JNIEnv* env, void* reserved)
{
  arrayListClass = new JniGlobalReference<jclass>(env, env->FindClass("java/util/ArrayList"));
  arrayListCtor = env->GetMethodID(arrayListClass->Get(), "<init>", "()V");

  filterClass = new JniGlobalReference<jclass>(env, env->FindClass(PKG("Filter")));
  filterCtor = env->GetMethodID(filterClass->Get(), "<init>", "(Ljava/lang/String;" TYP("Filter$Type") ")V");

  subscriptionClass = new JniGlobalReference<jclass>(env, env->FindClass(PKG("Subscription")));
  subscriptionCtor = env->GetMethodID(subscriptionClass->Get(), "<init>",
          "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;" TYP("FilterEngine") ")V");

  emulationSelectorClass = new JniGlobalReference<jclass>(env, env->FindClass(PKG("FilterEngine$EmulationSelector")));
  emulationSelectorCtor = env->GetMethodID(emulationSelectorClass->Get(), "<init>",
                                           "(Ljava/lang/String;Ljava/lang/String;)V");

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

bool stringBeginsWith(const std::string& string, const std::string& beginning)
{
  return string.compare(0, beginning.length(), beginning) == 0;
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

jmethodID JniGetListSizeMethod(JNIEnv* env, jobject list)
{
  JniLocalReference<jclass> clazz(env, env->GetObjectClass(list));
  return env->GetMethodID(*clazz, "size", "()I");
}

jint JniGetListSize(JNIEnv* env, jobject list, jmethodID getSizeMethod)
{
  return env->CallIntMethod(list, getSizeMethod);
}

jmethodID JniGetGetFromListMethod(JNIEnv* env, jobject list)
{
  JniLocalReference<jclass> clazz(env, env->GetObjectClass(list));
  return env->GetMethodID(*clazz, "get", "(I)Ljava/lang/Object;");
}

void JniAddObjectToList(JNIEnv* env, jobject list, jmethodID addMethod, jobject value)
{
  env->CallBooleanMethod(list, addMethod, value);
}

jobject JniGetObjectFromList(JNIEnv* env, jobject list, jmethodID getMethod, jint i)
{
  return env->CallObjectMethod(list, getMethod, i);
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
static jobject NewJniObject(JNIEnv* env, T&& value, jclass clazz, jmethodID ctor)
{
  return env->NewObject(clazz, ctor, JniPtrToLong(new T(std::forward<T>(value))));
}

jobject NewJniFilter(JNIEnv* env, AdblockPlus::Filter&& filter)
{
    return env->NewObject(filterClass->Get(),
                          filterCtor,
                          JniStdStringToJava(env, filter.GetRaw()),
                          GetJniTypeFromNativeType(env, filter.GetType()));
}

jobject NewJniSubscription(JNIEnv* env, AdblockPlus::Subscription&& subscription, jobject filterEngine)
{

  return env->NewObject(subscriptionClass->Get(),
                        subscriptionCtor,
                        env->NewStringUTF(subscription.GetUrl().c_str()),
                        env->NewStringUTF(subscription.GetTitle().c_str()),
                        env->NewStringUTF(subscription.GetHomepage().c_str()),
                        env->NewStringUTF(subscription.GetAuthor().c_str()),
                        env->NewStringUTF(joinStringVector(subscription.GetLanguages(), ",").c_str()),
                        filterEngine);
}

jobject NewJniEmulationSelector(JNIEnv* env, const AdblockPlus::IFilterEngine::EmulationSelector& emulationSelector)
{
  return env->NewObject(emulationSelectorClass->Get(), emulationSelectorCtor,
    JniStdStringToJava(env, emulationSelector.selector), JniStdStringToJava(env, emulationSelector.text));
}

jobject JniStringVectorToArrayList(JNIEnv* env, const std::vector<std::string>& stringVector)
{
  jobject arrayList = NewJniArrayList(env);

  for (auto it = stringVector.cbegin(), end = stringVector.cend(); it != end; ++it)
  {
    JniAddObjectToList(env, arrayList, JniStdStringToJava(env, *it));
  }

  return arrayList;
}
