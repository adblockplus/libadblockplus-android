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

#include "JSFunctionWrapper.h"

using namespace facebook::jni;
using namespace facebook::jsi;
using namespace AdblockPlus;

JSFunctionWrapperNative::JSFunctionWrapperNative(Runtime& rt, const facebook::jsi::Value *args, size_t count,
                                                 bool isImmediate)
{
  // There is/are at least one (setImmediate) or two (setTimeout) args:
  // 1st is a Function, 2nd is a Timeout (for setTimeout), the rest can be Function args.
  for (size_t i = 0; i < count; ++i)
  {
    if (!isImmediate && (i == 1)) //timeout
    {
      millis = args[i].asNumber();
    }
    else
    {
      // Would be nice to move not copy but some types are not moveable
      jsCallbackArguments.push_back(facebook::jsi::Value(rt, args[i]));
    }
  }
}

local_ref<JSFunctionWrapper> JSFunctionWrapper::create(jlong nativePtr)
{
   return newInstance(nativePtr);
}
