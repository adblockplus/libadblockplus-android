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
#include "JniFilter.h"

// precached in JNI_OnLoad and released in JNI_OnUnload
JniGlobalReference<jclass>* filterEnumClass;

void JniFilter_OnLoad(JavaVM* vm, JNIEnv* env, void* reserved)
{
  filterEnumClass = new JniGlobalReference<jclass>(env, env->FindClass(PKG("Filter$Type")));
}

void JniFilter_OnUnload(JavaVM* vm, JNIEnv* env, void* reserved)
{
  if (filterEnumClass)
  {
    delete filterEnumClass;
    filterEnumClass = NULL;
  }
}

jobject GetJniTypeFromNativeType(JNIEnv *pEnv, AdblockPlus::Filter::Type type) {
  const char *enumName;

  switch (type) {
    case AdblockPlus::IFilterImplementation::TYPE_BLOCKING:
      enumName = "BLOCKING";
          break;
    case AdblockPlus::IFilterImplementation::TYPE_COMMENT:
      enumName = "COMMENT";
          break;
    case AdblockPlus::IFilterImplementation::TYPE_ELEMHIDE:
      enumName = "ELEMHIDE";
          break;
    case AdblockPlus::IFilterImplementation::TYPE_ELEMHIDE_EXCEPTION:
      enumName = "ELEMHIDE_EXCEPTION";
          break;
    case AdblockPlus::IFilterImplementation::TYPE_ELEMHIDE_EMULATION:
      enumName = "ELEMHIDE_EMULATION";
          break;
    case AdblockPlus::IFilterImplementation::TYPE_EXCEPTION:
      enumName = "EXCEPTION";
          break;
    default:
      enumName = "INVALID";
          break;
  }

  jfieldID enumField = pEnv->GetStaticFieldID(filterEnumClass->Get(),
                                              enumName, TYP("Filter$Type"));
  return pEnv->GetStaticObjectField(filterEnumClass->Get(), enumField);
}
