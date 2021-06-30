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

#include "JsUtils.h"

using namespace facebook::jsi;
using namespace AdblockPlus;

void JsUtils::throwJsIfNotAFunction(facebook::jsi::Runtime &rt, const facebook::jsi::Value *pArg, const char *message)
{
   if (!pArg->isObject() || !pArg->asObject(rt).isFunction(rt))
   {
      throw facebook::jsi::JSError(rt, message);
   }
}

void JsUtils::throwJsIfNotANumber(facebook::jsi::Runtime &rt, const facebook::jsi::Value *pArg, const char *message)
{
   if (!pArg->isNumber())
   {
      throw facebook::jsi::JSError(rt, message);
   }
}

void JsUtils::throwJsIfNotAString(facebook::jsi::Runtime &rt, const facebook::jsi::Value *pArg, const char *message)
{
   if (!pArg->isString())
   {
     throw facebook::jsi::JSError(rt, message);
   }
}

