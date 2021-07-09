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

#ifndef JS_FUNCTION_WRAPPER_H
#define JS_FUNCTION_WRAPPER_H

#include <vector>
#include <jsi/jsi/jsi.h>
#include <fbjni/fbjni.h>

namespace AdblockPlus
{
  struct JSFunctionWrapperNative
  {
     jlong millis = 0;
     std::vector<facebook::jsi::Value> jsCallbackArguments;
     JSFunctionWrapperNative(facebook::jsi::Runtime& rt, const facebook::jsi::Value *args, size_t count, bool isImmediate);
  };

  struct JSFunctionWrapper : facebook::jni::JavaClass<JSFunctionWrapper>
  {
     static constexpr auto kJavaDescriptor = "Lorg/adblockplus/android/JSFunctionWrapper;";
     static facebook::jni::local_ref<JSFunctionWrapper> create(jlong nativePtr);
  };
}
#endif
