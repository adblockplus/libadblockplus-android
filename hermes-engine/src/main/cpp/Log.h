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

#ifndef HERMESENGINE_LOG_H
#define HERMESENGINE_LOG_H


namespace AdblockPlus
{
    namespace Log
    {
        // For JavaScript log forwarding
        void jsLog(const char *level, const char *msg);
    }
}

#ifdef __ANDROID__

#include <android/log.h>

namespace AdblockPlus
{
    namespace Log
    {
        // For C++ logging

        inline void error(const char* tag, const char* msg)
        {
          __android_log_write(ANDROID_LOG_ERROR, tag, msg);
        }

        template<typename... VA_ARGS>
        inline void error(const char* tag, const char* msg, VA_ARGS... args)
        {
          __android_log_print(ANDROID_LOG_ERROR, tag, msg, args...);
        }

        inline void info(const char* tag, const char* msg)
        {
          __android_log_write(ANDROID_LOG_INFO, tag, msg);
        }

        template<typename... VA_ARGS>
        inline void info(const char* tag, const char* msg, VA_ARGS... args)
        {
          __android_log_print(ANDROID_LOG_INFO, tag, msg, args...);
        }

        #ifndef LOG_TAG
        #define LOG_TAG "log"
        #endif

        #define ABP_LOG_ERROR(...) ::AdblockPlus::Log::error(LOG_TAG, __VA_ARGS__)
        #define ABP_LOG_INFO(...) ::AdblockPlus::Log::info(LOG_TAG, __VA_ARGS__)
    }
}

#else

namespace AdblockPlus
{
    namespace Log
    {
        inline void error(const char* tag, const char* msg)
        {
            (void) tag; (void) msg;
        }

        template<typename... VA_ARGS>
        inline void error(const char* tag, const char* msg, VA_ARGS... args)
        {
            (void) tag; (void) msg;
        }

        inline void info(const char* tag, const char* msg)
        {
            (void) tag; (void) msg;
        }

        template<typename... VA_ARGS>
        inline void info(const char* tag, const char* msg, VA_ARGS... args)
        {
            (void) tag; (void) msg;
        }
    }
}

#define ABP_LOG_ERROR(...) ((void)0)
#define ABP_LOG_INFO(...) ((void)0)

#endif

#endif
