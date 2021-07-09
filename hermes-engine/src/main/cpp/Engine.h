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

#ifndef HERMESENGINE_ENGINE_H
#define HERMESENGINE_ENGINE_H

#include <string>

#include <fbjni/ByteBuffer.h>
#include <jsi/jsi/jsi.h>

#include "JSFunctionWrapper.h"

using namespace facebook::jni;

typedef local_ref<JArrayClass<jobject>> JObjectArray;

namespace facebook
{
  namespace hermes
  {
    class HermesRuntime;
  }
}

struct AdblockEngine : JavaClass<AdblockEngine>
{
  static constexpr auto kJavaDescriptor = "Lorg/adblockplus/android/AdblockEngine;";

  static void init(alias_ref<AdblockEngine> thiz,
                   alias_ref<JString> baseDataFolder,
                   alias_ref<JString> coreJsFilePath);

  static std::string evaluateJS(alias_ref<AdblockEngine> thiz,
                                alias_ref<JString> src);

  static void _executeJSFunction(alias_ref<AdblockEngine> thiz, alias_ref<AdblockPlus::JSFunctionWrapper> jsFunctionWrapper);

  static jboolean _isContentAllowlisted(alias_ref<AdblockEngine> thiz,
                                        int contentTypeMask,
                                        alias_ref<JList<JString> > referrerChain,
                                        alias_ref<JString> siteKey);

  static std::string _matches(alias_ref<AdblockEngine> thiz,
                              alias_ref<JString> url,
                              int contentTypeMask,
                              alias_ref<JString> parent,
                              alias_ref<JString> siteKey,
                              jboolean specificOnly);

  static std::string _getElementHidingStyleSheet(alias_ref<AdblockEngine> thiz,
                                                 alias_ref<JString> domain,
                                                 jboolean specificOnly);

  static JObjectArray _getElementHidingEmulationSelectors(alias_ref<AdblockEngine> thiz,
                                                          alias_ref<JString> domain);

  static void registerNatives();

  static void StoreCallback(facebook::jsi::Runtime& rt, const facebook::jsi::Value *args, size_t count,
                            bool isImmediate);

  static void InitDone(bool success);

private:
  static facebook::hermes::HermesRuntime* getRuntimePtr(alias_ref<AdblockEngine> thiz);
};

#endif //HERMESENGINE_ENGINE_H
