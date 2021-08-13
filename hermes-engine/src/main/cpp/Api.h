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

#ifndef HERMESENGINE_API_H
#define HERMESENGINE_API_H

#include "jsi/jsi.h"
#include "Engine.h"

using hermesptr = facebook::hermes::HermesRuntime*;
using Function = facebook::jsi::Function;

class Api
{
public:
  static jboolean isContentAllowlisted(hermesptr runtime, jint contentTypeMask,
                                       alias_ref<JList<JString> > referrerChain,
                                       alias_ref<JString> siteKey);

  static std::string matches(hermesptr runtime, alias_ref<JString> url, jint contentTypeMask,
                             alias_ref<JString> parent, alias_ref<JString> siteKey, jboolean specificOnly);

  static std::string getElementHidingStyleSheet(hermesptr runtime, alias_ref<JString> domain, jboolean specificOnly);

  static JObjectArrayLocalRef getElementHidingEmulationSelectors(hermesptr runtime, alias_ref<JString> domain);

  static void addCustomFilter(hermesptr runtime, alias_ref<JString> filter);

  static void removeCustomFilter(hermesptr runtime, alias_ref<JString> filter);

  static jboolean isListedSubscription(hermesptr runtime, alias_ref<JString> subscriptionUrl);

  static void addSubscription(hermesptr runtime, alias_ref<JString> subscriptionUrl);

  static void removeSubscription(hermesptr runtime, alias_ref<JString> subscriptionUrl);

  static JObjectArrayLocalRef getListedSubscriptionUrls(hermesptr runtime, alias_ref<AdblockEngine> thiz);

  static JObjectLocalRef getSubscriptionByUrl(hermesptr runtime, alias_ref<JString> subscriptionUrl);

  static void setAASubscriptionEnabled(hermesptr runtime, jboolean enabled);

private:
  static Function getFunction(hermesptr runtime, const char* functionName);
};


#endif //HERMESENGINE_API_H
