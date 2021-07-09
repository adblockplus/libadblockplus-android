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

(function()
{
  'use strict';
  var global = (0, eval)('this');
  if (!global.console)
  {
    global.console = {};
  }
  var con = global.console;
  con.error = function()
  {
    __log("error", arguments[0]);
  }
  con.debug = function()
  {
    __log("debug", arguments[0]);
  }
  con.log = function()
  {
    __log("info", arguments[0]);
  }
  con.warn = function()
  {
    __log("warn", arguments[0]);
  }
  var prop, method;
  var properties = ['memory'];
  var methods = ('assert,clear,count,debug,dir,dirxml,error,exception,group,' +
     'groupCollapsed,groupEnd,info,log,markTimeline,profile,profiles,profileEnd,' +
     'show,table,time,timeEnd,timeline,timelineEnd,timeStamp,trace,warn,timeLog,trace').split(',');
  while (prop = properties.pop())
  {
    if (!con[prop])
    {
      con[prop] = {};
    }
  }
  while (method = methods.pop())
  {
    if (!con[method])
    {
      con[method] = function() {};
    }
  }
}
)();
