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

#ifndef HERMESENGINE_JS_UTILS_H
#define HERMESENGINE_JS_UTILS_H

#include "jsi/jsi.h"

namespace AdblockPlus
{
   namespace JsUtils
   {
      void throwJsIfNotABoolean(facebook::jsi::Runtime &rt, const facebook::jsi::Value *pArg, const char *message);
      void throwJsIfNotAFunction(facebook::jsi::Runtime &rt, const facebook::jsi::Value *pArg, const char *message);
      void throwJsIfNotANumber(facebook::jsi::Runtime &rt, const facebook::jsi::Value *pArg, const char *message);
      void throwJsIfNotAString(facebook::jsi::Runtime &rt, const facebook::jsi::Value *pArg, const char *message);
   }
}

#endif // HERMESENGINE_JS_UTILS_H
