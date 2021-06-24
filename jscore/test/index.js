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

/**
 * This is an interim entry point for running simple test validations
 *
 * HOWTO validate `npm run test`
 *
 * Make sure you have compiled hermes and hermesc
 * (see README.md for instructions)
 */

import * as API from "../lib/api";
import {Prefs} from "prefs";
import {IO} from "io";
import * as Info from "info";

function assert(statement, message)
{
  if (!statement)
    throw message || "Assert failed";
}

function testApi()
{
  try
  {
    API.checkFilterMatch("https://eyeo.com", 0, "https://eyeo.com", "", true);
    print("Ok API");
  }
  catch (e)
  {
    print("Fail API");
    print(e.stack);
  }
}

function testPrefs()
{
  try
  {
    Prefs.off();
    print("Ok Prefs.off()");
    assert(Prefs.enabled == false, "Prefs.enabled should be false");

    Prefs.on();
    print("Ok Prefs.on()");
    assert(Prefs.enabled == true, "Prefs.enabled should be true");

    print("Prefs.enabled: " + Prefs.enabled);
  }
  catch (e)
  {
    print("Fail Prefs");
    print(e.stack);
  }
}

function testInfo()
{
  try
  {
    print("Ok addonName = libadblockplus-android " + Info.addonVersion);
    print("Ok addonVersion " + Info.addonVersion);
    print("Ok application " + Info.application);
    print("Ok applicationVersion " + Info.applicationVersion);
    print("Ok platform " + Info.platform);
    print("Ok platformVersion " + Info.platformVersion);
  }
  catch (e)
  {
    print("Fail Info");
    print(e.stack);
  }
}

function testPromises()
{
  assert(HermesInternal.hasPromise(), "Promises should be enabled");
  print("Ok Promises are enabled");

  let promise = new Promise((res, rej) =>
  {
    res("success!");
  });

  promise.then(message =>
  {
    assert(message == "success!", "Promise should be resolved");
    print("Ok Promises if this gets printed AFTER all tests");
  });
}

testPromises();
testApi();
testPrefs();
testInfo();
