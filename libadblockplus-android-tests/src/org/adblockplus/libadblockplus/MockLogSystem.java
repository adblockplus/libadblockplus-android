/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-2016 Eyeo GmbH
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

package org.adblockplus.libadblockplus;

public class MockLogSystem extends LogSystem
{
    private LogLevel lastLogLevel;

    public LogLevel getLastLogLevel()
    {
        return lastLogLevel;
    }

    private String lastMessage;

    public String getLastMessage()
    {
        return lastMessage;
    }

    private String lastSource;

    public String getLastSource()
    {
        return lastSource;
    }

    @Override
    public void logCallback(LogLevel logLevel, String message, String source)
    {
        lastLogLevel = logLevel;
        lastMessage = message;
        lastSource = source;
    }
}
