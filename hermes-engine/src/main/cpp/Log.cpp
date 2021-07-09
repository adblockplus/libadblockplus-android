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

#include "Log.h"
#include <string>

using namespace std;

#ifdef __ANDROID__

const string DEBUG = "debug";
const string ERROR = "error";
const string WARN = "warn";

int getAndroidLogLevel(string level)
{
    if (DEBUG == level) return ANDROID_LOG_DEBUG;
    if (ERROR == level) return ANDROID_LOG_ERROR;
    if (WARN == level) return ANDROID_LOG_WARN;
    return ANDROID_LOG_INFO;
}

void AdblockPlus::Log::jsLog(const char *level, const char *msg)
{
    __android_log_write(getAndroidLogLevel(level), "JS", msg);
}

#else

void AdblockPlus::Log::jsLog(const char *level, const char *msg)
{
    (void) level; (void) msg;
}

#endif
