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

#include <hermes/hermes.h>

#include "Engine.h"
#include "GlobalJsObject.h"
#include "JsUtils.h"
#include "Log.h"

using namespace facebook::hermes;
using namespace facebook::jsi;
using namespace AdblockPlus;

namespace
{
   int SetTimeoutCallback(Runtime &rt, const Value &thisVal, const Value *args, size_t count)
   {
      if (count < 2)
      {
         throw JSError(rt, "setTimeout requires 2 parameters");
      }

      JsUtils::throwJsIfNotAFunction(rt, &args[0], "First argument to setTimeout"
                                                  " must be a function");
      JsUtils::throwJsIfNotANumber(rt, &args[1], "Second argument to setTimeout"
                                                    " must be a number");

      Engine::StoreCallback(rt, args, count, false);

      // We should actually return the timer ID here, which could be
      // used via clearTimeout(). But since we don't seem to need
      // clearTimeout(), we can save that for later.
      return 0;
   }

    int SetImmediateCallback(Runtime &rt, const Value &thisVal, const Value *args, size_t count)
    {
       if (count < 1)
       {
          throw JSError(rt, "setImmediate requires 1 parameter");
       }

       JsUtils::throwJsIfNotAFunction(rt, &args[0], "First argument to setImmediate"
                                                    " must be a function");

       Engine::StoreCallback(rt, args, count, true);

       return 0;
    }

    int InitDoneCallback(Runtime &rt, const Value &thisVal, const Value *args, size_t count)
    {
       if (count < 1)
       {
          throw JSError(rt, "__initDone requires 1 parameter");
       }
       JsUtils::throwJsIfNotABoolean(rt, &args[0], "First argument to __initDone"
                                                  " must be a boolean");
       Engine::InitDone(args[0].getBool());
       return 0;
    }

    int LogCallback(Runtime &rt, const Value &thisVal, const Value *args, size_t count)
    {
       if (count < 2)
       {
          throw JSError(rt, "__log requires 2 parameters");
       }
       JsUtils::throwJsIfNotAString(rt, &args[0], "First argument to __log"
                                                   " must be a log level string");
       std::string level = args[0].asString(rt).utf8(rt);
       JsUtils::throwJsIfNotAString(rt, &args[1], "Second argument to __log"
                                                   " must be a log string");
       std::string message = args[1].asString(rt).utf8(rt);

       Log::jsLog(level.c_str(), message.c_str());
       return 0;
    }
}

void GlobalJsObject::Setup(Runtime *pRuntime)
{
   const PropNameID setTimeoutId = PropNameID::forAscii(*pRuntime,"setTimeout");
   auto jsSetTimeout = Function::createFromHostFunction(*pRuntime, setTimeoutId, 2, SetTimeoutCallback);
   pRuntime->global().setProperty(*pRuntime, setTimeoutId, jsSetTimeout);
   const PropNameID setImmediateId = PropNameID::forAscii(*pRuntime,"setImmediate");
   auto jsSetImmediate = Function::createFromHostFunction(*pRuntime, setImmediateId, 1, SetImmediateCallback);
   pRuntime->global().setProperty(*pRuntime, setImmediateId, jsSetImmediate);
   const PropNameID initDoneId = PropNameID::forAscii(*pRuntime,"__initDone");
   auto jsInitDone = Function::createFromHostFunction(*pRuntime, initDoneId, 1, InitDoneCallback);
   pRuntime->global().setProperty(*pRuntime, initDoneId, jsInitDone);
   const PropNameID logId = PropNameID::forAscii(*pRuntime,"__log");
   auto jsLog = Function::createFromHostFunction(*pRuntime, logId, 2, LogCallback);
   pRuntime->global().setProperty(*pRuntime, logId, jsLog);
}
